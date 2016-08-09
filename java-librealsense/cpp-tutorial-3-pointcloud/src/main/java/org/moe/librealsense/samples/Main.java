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
import org.moe.natj.cxx.ann.CxxConstructor;
import org.moe.natj.general.ptr.ConstBytePtr;
import org.moe.natj.general.ptr.ConstCharPtr;
import org.moe.librealsense.RS;
import com.jogamp.opengl.GL2;
import com.jogamp.opengl.GLAutoDrawable;
import com.jogamp.opengl.GLCapabilities;
import com.jogamp.opengl.GLEventListener;
import com.jogamp.opengl.awt.GLCanvas;
import com.jogamp.opengl.glu.GLU;
import com.jogamp.opengl.util.Animator;

import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import static com.jogamp.opengl.GL.*;
import static com.jogamp.opengl.fixedfunc.GLMatrixFunc.GL_MODELVIEW;
import static com.jogamp.opengl.fixedfunc.GLMatrixFunc.GL_PROJECTION;

public class Main extends Frame {

    public static void main(String[] args) {
        // Load natives
        System.loadLibrary("natj");
        System.loadLibrary("java-realsense");

        // Start app
        new Main().run();
    }

    private void run() {
        // Turn on logging. We can separately enable logging to console or to file, and use different severity filters for each.
        RS.log_to_console(RS.log_severity.warn.value);

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
        dev.enable_stream(RS.stream.depth.value, RS.preset.best_quality.value);
        dev.enable_stream(RS.stream.color.value, RS.preset.best_quality.value);
        dev.start();

        final Window window = new Window(ctx, dev);
        window.setVisible(true);
    }

    public static class Window extends Frame implements GLEventListener {

        private final RS.context ctx;
        private final RS.device dev;

        private double yaw, pitch, lastX, lastY;
        private boolean ml;

        private Window(RS.context ctx, RS.device dev) {
            super("librealsense tutorial #3");

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

        private class MouseListener extends MouseInputAdapter {
            @Override
            public void mousePressed(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1)
                    ml = true;
                lastX = e.getX();
                lastY = e.getY();
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                on_cursor_pos(e.getX(), e.getY());
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.getButton() == MouseEvent.BUTTON1)
                    ml = false;
            }
        }

        private Animator setupJOGL() {
            GLCapabilities caps = new GLCapabilities(null);
            caps.setDoubleBuffered(true);
            caps.setHardwareAccelerated(true);

            GLCanvas canvas = new GLCanvas(caps);
            canvas.addGLEventListener(this);
            add(canvas, BorderLayout.CENTER);

            MouseListener mouseListener = new MouseListener();
            canvas.addMouseListener(mouseListener);
            canvas.addMouseMotionListener(mouseListener);

            Animator anim = new Animator(canvas);
            anim.start();

            return anim;
        }

        private static double clamp(double val, double lo, double hi) {
            return val < lo ? lo : val > hi ? hi : val;
        }

        private void on_cursor_pos(double x, double y) {
            if (ml) {
                yaw = clamp(yaw - (x - lastX), -120, 120);
                pitch = clamp(pitch + (y - lastY), -80, 80);
            }
            lastX = x;
            lastY = y;
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

        private char[] depth_data;
        private byte[] color_data;

        private long timestamp = System.currentTimeMillis();
        private int numFrames = 0;

        @Override
        public void display(GLAutoDrawable drawable) {
            ++numFrames;
            if (timestamp + 1000 < System.currentTimeMillis()) {
                timestamp += 1000;
                System.out.println("FPS: " + numFrames);
                numFrames = 0;
            }

            final GL2 gl = (GL2) drawable.getGL();
            final GLU glu = GLU.createGLU(gl);

            dev.wait_for_frames();

            // Retrieve camera parameters for mapping between depth and color
            RS.intrinsics depth_intrin = dev.get_stream_intrinsics(RS.stream.depth.value);
            RS.extrinsics depth_to_color = dev.get_extrinsics(RS.stream.depth.value, RS.stream.color.value);
            RS.intrinsics color_intrin = dev.get_stream_intrinsics(RS.stream.color.value);
            float scale = dev.get_depth_scale();

            // Cache some values to ease JNI tasks
            final int depth_intrinWidth = depth_intrin.getWidth();
            final int depth_intrinHeight = depth_intrin.getHeight();
            final int color_intrinWidth = color_intrin.getWidth();
            final int color_intrinHeight = color_intrin.getHeight();
            final RS.float2 depth_pixel = createFloat2();

            // Retrieve our images
            {
                ConstCharPtr depth_image = dev.get_frame_data(RS.stream.depth.value).getCharPtr();
                if (depth_data == null) {
                    depth_data = depth_image.toCharArray(depth_intrinWidth * depth_intrinHeight);
                } else {
                    depth_image.copyTo(depth_data);
                }
                ConstBytePtr color_image = dev.get_frame_data(RS.stream.color.value).getBytePtr();
                if (color_data == null) {
                    color_data = color_image.toByteArray(color_intrinWidth * color_intrinHeight * 3);
                } else {
                    color_image.copyTo(color_data);
                }
            }

            // Set up a perspective transform in a space that we can rotate by clicking and dragging the mouse
            gl.glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            gl.glMatrixMode(GL_PROJECTION);
            gl.glLoadIdentity();
            glu.gluPerspective(60, (float) 1280 / 960, 0.01f, 20.0f);
            gl.glMatrixMode(GL_MODELVIEW);
            gl.glLoadIdentity();
            glu.gluLookAt(0, 0, 0, 0, 0, 1, 0, -1, 0);
            gl.glTranslatef(0, 0, +0.5f);
            gl.glRotated(pitch, 1, 0, 0);
            gl.glRotated(yaw, 0, 1, 0);
            gl.glTranslatef(0, 0, -0.5f);

            // We will render our depth data as a set of points in 3D space
            gl.glPointSize(2);
            gl.glEnable(GL_DEPTH_TEST);
            gl.glBegin(GL_POINTS);

            for (int dy = 0; dy < depth_intrinHeight; ++dy) {
                for (int dx = 0; dx < depth_intrinWidth; ++dx) {
                    // Retrieve the 16-bit depth value and map it into a depth in meters
                    char depth_value = depth_data[dy * depth_intrinWidth + dx];
                    float depth_in_meters = depth_value * scale;

                    // Skip over pixels with a depth value of zero, which is used to indicate no data
                    if (depth_value == 0) continue;

                    // Map from pixel coordinates in the depth image to pixel coordinates in the color image
                    depth_pixel.setX((float) dx);
                    depth_pixel.setY((float) dy);
                    RS.float3 depth_point = depth_intrin.deproject(depth_pixel, depth_in_meters);
                    RS.float3 color_point = depth_to_color.transform(depth_point);
                    RS.float2 color_pixel = color_intrin.project(color_point);

                    // Use the color from the nearest color pixel, or pure white if this point falls outside the color image
                    final int cx = Math.round(color_pixel.getX()), cy = Math.round(color_pixel.getY());
                    if (cx < 0 || cy < 0 || cx >= color_intrinWidth || cy >= color_intrinHeight) {
                        gl.glColor3ub((byte) 255, (byte) 255, (byte) 255);
                    } else {
                        gl.glColor3ubv(color_data, (cy * color_intrinWidth + cx) * 3);
                    }

                    // Emit a vertex at the 3D location of this depth pixel
                    gl.glVertex3f(depth_point.getX(), depth_point.getY(), depth_point.getZ());

                    // Do some C++ cleanup
                    CxxRuntime.delete(depth_point);
                    CxxRuntime.delete(color_point);
                    CxxRuntime.delete(color_pixel);
                }
            }
            gl.glEnd();

            gl.glFlush();

            // Do some C++ cleanup
            CxxRuntime.delete(depth_pixel);
            CxxRuntime.delete(depth_intrin);
            CxxRuntime.delete(depth_to_color);
            CxxRuntime.delete(color_intrin);
        }

        @Override
        public void dispose(GLAutoDrawable drawable) {
        }
    }

    @CxxConstructor
    private static native RS.float2 createFloat2();

    private static void printf(String fmt, Object... p) {
        System.out.printf(fmt, p);
    }
}
