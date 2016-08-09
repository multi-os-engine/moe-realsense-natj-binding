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
            super("librealsense tutorial #3 optimized");

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
            RS.intrinsics depth_intrin_tmp = dev.get_stream_intrinsics(RS.stream.depth.value);
            Intrinsics depth_intrin = new Intrinsics(depth_intrin_tmp);
            CxxRuntime.delete(depth_intrin_tmp);

            RS.extrinsics depth_to_color_tmp = dev.get_extrinsics(RS.stream.depth.value, RS.stream.color.value);
            Extrinsics depth_to_color = new Extrinsics(depth_to_color_tmp);
            CxxRuntime.delete(depth_to_color_tmp);

            RS.intrinsics color_intrin_tmp = dev.get_stream_intrinsics(RS.stream.color.value);
            Intrinsics color_intrin = new Intrinsics(color_intrin_tmp);
            CxxRuntime.delete(color_intrin_tmp);

            float scale = dev.get_depth_scale();

            // Retrieve our images
            {
                ConstCharPtr depth_image = dev.get_frame_data(RS.stream.depth.value).getCharPtr();
                if (depth_data == null) {
                    depth_data = depth_image.toCharArray(depth_intrin.width * depth_intrin.height);
                } else {
                    depth_image.copyTo(depth_data);
                }
                ConstBytePtr color_image = dev.get_frame_data(RS.stream.color.value).getBytePtr();
                if (color_data == null) {
                    color_data = color_image.toByteArray(color_intrin.width * color_intrin.height * 3);
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

            final float depth_pixel[] = new float[2];
            final float depth_point[] = new float[3];
            final float color_point[] = new float[3];
            final float color_pixel[] = new float[2];

            for (int dy = 0; dy < depth_intrin.height; ++dy) {
                for (int dx = 0; dx < depth_intrin.width; ++dx) {
                    // Retrieve the 16-bit depth value and map it into a depth in meters
                    char depth_value = depth_data[dy * depth_intrin.width + dx];
                    float depth_in_meters = depth_value * scale;

                    // Skip over pixels with a depth value of zero, which is used to indicate no data
                    if (depth_value == 0) continue;

                    // Map from pixel coordinates in the depth image to pixel coordinates in the color image
                    depth_pixel[0] = dx;
                    depth_pixel[1] = dy;

                    depth_intrin.deproject(depth_point, depth_pixel, depth_in_meters);
                    depth_to_color.transform(color_point, depth_point);
                    color_intrin.project(color_pixel, color_point);

                    // Use the color from the nearest color pixel, or pure white if this point falls outside the color image
                    final int cx = Math.round(color_pixel[0]), cy = Math.round(color_pixel[1]);
                    if (cx < 0 || cy < 0 || cx >= color_intrin.width || cy >= color_intrin.height) {
                        gl.glColor3ub((byte) 255, (byte) 255, (byte) 255);
                    } else {
                        final int v_offset = (cy * color_intrin.width + cx) * 3;
                        gl.glColor3ub(color_data[v_offset], color_data[v_offset + 1], color_data[v_offset + 2]);
                    }

                    // Emit a vertex at the 3D location of this depth pixel
                    gl.glVertex3f(depth_point[0], depth_point[1], depth_point[2]);
                }
            }
            gl.glEnd();

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
