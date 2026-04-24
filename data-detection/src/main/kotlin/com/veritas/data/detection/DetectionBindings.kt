package com.veritas.data.detection

import com.veritas.domain.detection.DetectionPipeline
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DetectionBindings {
    @Binds
    @Singleton
    abstract fun bindDetectionPipeline(
        provenancePipeline: ProvenancePipeline,
    ): DetectionPipeline
}
