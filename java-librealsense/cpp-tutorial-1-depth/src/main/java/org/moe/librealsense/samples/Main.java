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
import org.moe.natj.general.ptr.ConstCharPtr;
import org.moe.librealsense.RS;

import java.util.concurrent.atomic.AtomicBoolean;

public class Main {
    private static final int EXIT_FAILURE = 1;
    private static final int EXIT_SUCCESS = 0;

    private static final AtomicBoolean isRunning = new AtomicBoolean(true);

    public static void main(String[] args) {
        // Load natives
        System.loadLibrary("natj");
        System.loadLibrary("java-realsense");

        // Create input listening thread for proper exit
        final Thread thread = new Thread(() -> {
            try {
                System.in.read();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            isRunning.set(false);
        });
        thread.setName("Exit listener");
        thread.setDaemon(true);
        thread.start();

        // Start app
        System.exit(new Main().run());
    }

    private int run() {
        // Create a context object. This object owns the handles to all connected realsense devices.
        final RS.context ctx = RS.contextCreate();
        printf("There are %d connected RealSense devices.\n", ctx.get_device_count());
        if (ctx.get_device_count() == 0) {
            CxxRuntime.delete(ctx);
            return EXIT_FAILURE;
        }

        // This tutorial will access only a single device, but it is trivial to extend to multiple devices
        RS.device dev = ctx.get_device(0);
        printf("\nUsing device 0, an %s\n", dev.get_name().toASCIIString());
        printf("    Serial number: %s\n", dev.get_serial().toASCIIString());
        printf("    Firmware version: %s\n", dev.get_firmware_version().toASCIIString());

        // Configure depth to run at VGA resolution at 30 frames per second
        dev.enable_stream(RS.stream.depth.value, 640, 480, RS.format.z16.value, 30);
        dev.start();

        // Determine depth value corresponding to one meter
        final char one_meter = (char) (1.0f / dev.get_depth_scale());

        // Print a simple text-based representation of the image, by breaking it into 10x20 pixel regions and and approximating the coverage of pixels within one meter
        final byte buffer[] = new byte[(640 / 10 + 1) * (480 / 20) + 1];
        while (isRunning.get()) {
            // This call waits until a new coherent set of frames is available on a device
            // Calls to get_frame_data(...) and get_frame_timestamp(...) on a device will return stable values until wait_for_frames(...) is called
            dev.wait_for_frames();

            // Retrieve depth data, which was previously configured as a 640 x 480 image of 16-bit depth values
            final ConstCharPtr depth_frame = dev.get_frame_data(RS.stream.depth.value).getCharPtr();

            int buffer_idx = 0;
            int coverage[] = new int[64];
            int depth_frame_idx = 0;
            for (int y = 0; y < 480; ++y) {
                for (int x = 0; x < 640; ++x) {
                    int depth = depth_frame.getValue(depth_frame_idx++);
                    if (depth > 0 && depth < one_meter) ++coverage[x / 10];
                }

                if (y % 20 == 19) {
                    for (int c = 0, coverageLength = coverage.length; c < coverageLength; c++) {
                        buffer[buffer_idx++] = (byte) " .:nhBXWW".charAt(coverage[c] / 25);
                        coverage[c] = 0;
                    }
                    buffer[buffer_idx++] = '\n';
                }
            }
            buffer[buffer_idx++] = '\n';
            System.out.write(buffer, 0, buffer_idx);
        }

        // Stop the stream and clean up
        dev.stop();
        dev.disable_stream(RS.stream.depth.value);
        CxxRuntime.delete(ctx);

        return EXIT_SUCCESS;
    }

    private static void printf(String fmt, Object... p) {
        System.out.printf(fmt, p);
    }
}
