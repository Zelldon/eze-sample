package org.camunda.community.eze

import com.hazelcast.client.HazelcastClient
import com.hazelcast.core.HazelcastInstance
import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.protocol.record.intent.DeploymentIntent
import io.zeebe.exporter.proto.Schema
import io.zeebe.hazelcast.connect.java.ZeebeHazelcast
import io.zeebe.hazelcast.exporter.HazelcastExporter
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class EzeExporterSample {

    private lateinit var zeebeEngine: ZeebeEngine
    private lateinit var client: ZeebeClient

    private lateinit var hazelcastClient: HazelcastInstance

    @BeforeEach
    fun `setup grpc server`() {
        zeebeEngine = EngineFactory.create(exporters = listOf(HazelcastExporter()))
        zeebeEngine.start()

        client = zeebeEngine.createClient()

        hazelcastClient = HazelcastClient.newHazelcastClient()
    }

    @AfterEach
    fun `tear down`() {
        zeebeEngine.stop()
    }

    @Test
    fun `should deploy process`() {
        // given
        val process = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done()

        // when
        client.newDeployCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()

        // then
        val deployments = mutableListOf<Schema.DeploymentRecord>()

        ZeebeHazelcast.newBuilder(hazelcastClient)
            .addDeploymentListener(deployments::add)
            .readFromHead()
            .build()

        await.untilAsserted {
            val deploymentCreated =
                deployments.find { it.metadata.intent == DeploymentIntent.CREATED.name }

            assertThat(deploymentCreated).isNotNull
            deploymentCreated?.let {
                assertThat(it.processMetadataList[0].bpmnProcessId).isEqualTo("process")
                assertThat(it.processMetadataList[0].resourceName).isEqualTo("process.bpmn")
            }
        }
    }

}