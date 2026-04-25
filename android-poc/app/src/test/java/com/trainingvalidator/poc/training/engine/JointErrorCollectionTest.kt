package com.trainingvalidator.poc.training.engine

import org.junit.Assert.assertNotSame
import org.junit.Test

class JointErrorCollectionTest {

    @Test
    fun `collectJointErrors returns fresh list per call`() {
        val tracked = emptyList<com.trainingvalidator.poc.training.models.TrackedJoint>()
        val a = JointErrorCollection.collectJointErrors(tracked, emptyMap())
        val b = JointErrorCollection.collectJointErrors(tracked, emptyMap())
        assertNotSame("no shared list buffer between calls", a, b)
    }
}
