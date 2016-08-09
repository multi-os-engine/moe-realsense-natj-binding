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

public class Extrinsics {
    public final float rotation[];    /* column-major 3x3 rotation matrix */
    public final float translation[]; /* 3 element translation vector, in meters */

    public Extrinsics(RS.rs_extrinsics extrin) {
        this.rotation = extrin.getRotation().getFloatPtr().toFloatArray(9);
        this.translation = extrin.getTranslation().toFloatArray(3);
    }

    public void transform(float to_point[], float from_point[]) {
        final float[] rotation = this.rotation;
        final float[] translation = this.translation;
        to_point[0] = rotation[0] * from_point[0] + rotation[3] * from_point[1] + rotation[6] * from_point[2] + translation[0];
        to_point[1] = rotation[1] * from_point[0] + rotation[4] * from_point[1] + rotation[7] * from_point[2] + translation[1];
        to_point[2] = rotation[2] * from_point[0] + rotation[5] * from_point[1] + rotation[8] * from_point[2] + translation[2];
    }
}
