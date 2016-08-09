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

import org.moe.librealsense.RS;

public class Intrinsics {
    public final int width;      /* width of the image in pixels */
    public final int height;     /* height of the image in pixels */
    public final float ppx;      /* horizontal coordinate of the principal point of the image, as a pixel offset from the left edge */
    public final float ppy;      /* vertical coordinate of the principal point of the image, as a pixel offset from the top edge */
    public final float fx;       /* focal length of the image plane, as a multiple of pixel width */
    public final float fy;       /* focal length of the image plane, as a multiple of pixel height */
    public final int model;      /* distortion model of the image */
    public final float coeffs[]; /* distortion coefficients */

    public Intrinsics(RS.rs_intrinsics intrin) {
        this.width = intrin.getWidth();
        this.height = intrin.getHeight();
        this.ppx = intrin.getPpx();
        this.ppy = intrin.getPpy();
        this.fx = intrin.getFx();
        this.fy = intrin.getFy();
        this.model = intrin.getModel();
        this.coeffs = intrin.getCoeffs().toFloatArray(5);
    }

    public void project(float pixel[], float point[]) {
        assert (model != RS.distortion.inverse_brown_conrady.value); // Cannot project to an inverse-distorted image

        float x = point[0] / point[2], y = point[1] / point[2];
        if (model == RS.distortion.modified_brown_conrady.value) {
            float r2 = x * x + y * y;
            final float[] coeffs = this.coeffs;
            float f = 1 + coeffs[0] * r2 + coeffs[1] * r2 * r2 + coeffs[4] * r2 * r2 * r2;
            x *= f;
            y *= f;
            float dx = x + 2 * coeffs[2] * x * y + coeffs[3] * (r2 + 2 * x * x);
            float dy = y + 2 * coeffs[3] * x * y + coeffs[2] * (r2 + 2 * y * y);
            x = dx;
            y = dy;
        }
        pixel[0] = x * fx + ppx;
        pixel[1] = y * fy + ppy;
    }

    public void deproject(float point[], float pixel[], float depth) {
        assert (model != RS.distortion.modified_brown_conrady.value); // Cannot deproject from a forward-distorted image

        float x = (pixel[0] - ppx) / fx;
        float y = (pixel[1] - ppy) / fy;
        if (model == RS.distortion.inverse_brown_conrady.value) {
            float r2 = x * x + y * y;
            final float[] coeffs = this.coeffs;
            float f = 1 + coeffs[0] * r2 + coeffs[1] * r2 * r2 + coeffs[4] * r2 * r2 * r2;
            float ux = x * f + 2 * coeffs[2] * x * y + coeffs[3] * (r2 + 2 * x * x);
            float uy = y * f + 2 * coeffs[3] * x * y + coeffs[2] * (r2 + 2 * y * y);
            x = ux;
            y = uy;
        }
        point[0] = depth * x;
        point[1] = depth * y;
        point[2] = depth;
    }
}
