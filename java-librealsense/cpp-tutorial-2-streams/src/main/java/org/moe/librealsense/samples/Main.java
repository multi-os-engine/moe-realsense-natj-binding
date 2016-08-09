/*
Copyright 2014-2016 Intel Corporation

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.moe.librealsense.samples;

import org.moe.natj.cxx.CxxRuntime;
import org.moe.natj.cxx.StdException;
import org.moe.librealsense.RS;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.util.Animator;

import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.ByteBuffer;
import java.nio.ShortBuffer;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.GL2.GL_RED_SCALE;
import static com.jogamp.opengl.GL2ES2.GL_RED;

public class Main extends Frame {

    public static void main(String[] args) {
        // Load natives
        System.loadLibrary("natj");
        System.loadLibrary("java-realsense");

        // Start app
        new Main().run();
    }

    private void run() {
        // Create a context object. This object owns the handles to all connected realsense devices.
        final RS.context ctx = RS.contextCreate();
        printf("There are %d connected RealSense devices.\n", ctx.get_device_count());
        if (ctx.get_device_count() == 0) {
            CxxRuntime.delete(ctx);
            System.exit(1);
        }

        // This tutorial will access only a single device, but it is trivial to extend to multiple devices
        RS.device dev = ctx.get_device(0);
        printf("\nUsing device 0, an %s\n", dev.get_name().toASCIIString());
        printf("    Serial number: %s\n", dev.get_serial().toASCIIString());
        printf("    Firmware version: %s\n", dev.get_firmware_version().toASCIIString());

        // Configure depth to run at VGA resolution at 30 frames per second
        dev.enable_stream(RS.stream.depth.value, 640, 480, RS.format.z16.value, 60);
        dev.enable_stream(RS.stream.color.value, 640, 480, RS.format.rgb8.value, 60);
        dev.enable_stream(RS.stream.infrared.value, 640, 480, RS.format.y8.value, 60);
        try {
            dev.enable_stream(RS.stream.infrared2.value, 640, 480, RS.format.y8.value, 60);
        } catch (StdException ex) {
            printf("Device does not provide infrared2 stream.\n");
        }
        dev.start();

        final Window window = new Window(ctx, dev);
        window.setVisible(true);
    }

    public static class Window extends Frame implements GLEventListener {

        private final RS.context ctx;
        private final RS.device dev;

        private Window(RS.context ctx, RS.device dev) {
            super("librealsense tutorial #2");

            if (ctx == null) {
                throw new NullPointerException();
            }
            if (dev == null) {
                throw new NullPointerException();
            }
            this.ctx = ctx;
            this.dev = dev;

            setLayout(new BorderLayout());
            setSize(1280, 960);
            setLocation(40, 40);
            setVisible(true);

            final Animator animator = setupJOGL();

            addWindowListener(new WindowAdapter() {
                @Override
                public void windowClosing(WindowEvent e) {
                    animator.stop();
                    setVisible(false);

                    // Stop the stream and clean up
                    dev.stop();
                    dev.disable_stream(RS.stream.depth.value);
                    CxxRuntime.delete(ctx);

                    System.exit(0);
                }
            });
        }

        private Animator setupJOGL() {
            GLCapabilities caps = new GLCapabilities(null);
            caps.setDoubleBuffered(true);
            caps.setHardwareAccelerated(true);

            GLCanvas canvas = new GLCanvas(caps);
            canvas.addGLEventListener(this);
            add(canvas, BorderLayout.CENTER);

            Animator anim = new Animator(canvas);
            anim.start();

            return anim;
        }

        @Override
        public void init(GLAutoDrawable drawable) {
        }

        @Override
        public void reshape(GLAutoDrawable drawable,
                            int x,
                            int y,
                            int width,
                            int height) {
        }

        @Override
        public void display(GLAutoDrawable drawable) {
            GL2 gl = (GL2) drawable.getGL();

            // Wait for new frame data
            dev.wait_for_frames();

            gl.glClear(GL_COLOR_BUFFER_BIT);
            gl.glPixelZoom(1, -1);

            // Display depth data by linearly mapping depth between 0 and 2 meters to the red channel
            gl.glRasterPos2f(-1, 1);
            gl.glPixelTransferf(GL_RED_SCALE, 0xFFFF * dev.get_depth_scale() / 2.0f);
            final ShortBuffer depthBuffer = ShortBuffer.wrap(dev.get_frame_data(RS.stream.depth.value).getShortPtr()
                    .toShortArray(640 * 480));
            gl.glDrawPixels(640, 480, GL_RED, GL_UNSIGNED_SHORT, depthBuffer);
            gl.glPixelTransferf(GL_RED_SCALE, 1.0f);

            // Display color image as RGB triples
            gl.glRasterPos2f(0, 1);
            final ByteBuffer colorBuffer = ByteBuffer.wrap(dev.get_frame_data(RS.stream.color.value).getBytePtr()
                    .toByteArray(640 * 480 * 3));
            gl.glDrawPixels(640, 480, GL_RGB, GL_UNSIGNED_BYTE, colorBuffer);

            // Display infrared image by mapping IR intensity to visible luminance
            gl.glRasterPos2f(-1, 0);
            final ByteBuffer irBuffer = ByteBuffer.wrap(dev.get_frame_data(RS.stream.infrared.value).getBytePtr()
                    .toByteArray(640 * 480));
            gl.glDrawPixels(640, 480, GL_LUMINANCE, GL_UNSIGNED_BYTE, irBuffer);

            // Display second infrared image by mapping IR intensity to visible luminance
            if (dev.is_stream_enabled(RS.stream.infrared2.value)) {
                gl.glRasterPos2f(0, 0);
                final ByteBuffer ir2Buffer = ByteBuffer.wrap(dev.get_frame_data(RS.stream.infrared2.value).getBytePtr()
                        .toByteArray(640 * 480));
                gl.glDrawPixels(640, 480, GL_LUMINANCE, GL_UNSIGNED_BYTE, ir2Buffer);
            }

            gl.glFlush();
        }

        @Override
        public void dispose(GLAutoDrawable drawable) {
        }
    }

    private static void printf(String fmt, Object... p) {
        System.out.printf(fmt, p);
    }
}
