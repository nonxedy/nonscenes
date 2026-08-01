package com.nonxedy.interpolator

import com.nonxedy.model.CutsceneFrame
import com.nonxedy.model.playback.PathPoint

interface PathInterpolator {
    fun interpolate(frames: List<CutsceneFrame>, t: Double): PathPoint
}
