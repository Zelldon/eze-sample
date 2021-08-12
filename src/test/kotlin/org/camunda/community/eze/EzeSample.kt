package org.camunda.community.eze

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.DeploymentEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.Future

class EzeSample {

    lateinit var zeebe: ZeebeEngine
    lateinit var client: ZeebeClient

    @BeforeEach
    fun `start Zeebe and create a client`() {
        zeebe = EngineFactory.create()
        zeebe.start()

        client = zeebe.createClient()
    }

    @AfterEach
    fun `clean up`() {
        client.close()
        zeebe.stop()
    }

    @Test
    fun `should deploy process`() {
        // given
        val process = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done()

        // when
        val deployFuture: Future<DeploymentEvent> = client.newDeployCommand()
            .addProcessModel(process, "process.bpmn")
            .send()

        // then
        await.untilAsserted {
            assertThat(deployFuture).isDone
        }
    }

    @Test
    fun `should complete process instance`() {
        // given
        val process = Bpmn.createExecutableProcess("process")
            .startEvent()
            .endEvent()
            .done()

        client.newDeployCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()

        // when
        val processInstanceResult = client.newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(mapOf("x" to 1))
            .withResult()
            .send()
            .join()

        // then
        assertThat(processInstanceResult.variablesAsMap)
            .containsEntry("x", 1)
    }

    @Test
    fun `should complete process instance with job worker`() {
        // given
        val process = Bpmn.createExecutableProcess("process")
            .startEvent()
            .serviceTask("task-1").zeebeJobType("test")
            .endEvent()
            .done()

        client.newDeployCommand()
            .addProcessModel(process, "process.bpmn")
            .send()
            .join()

        client.newWorker().jobType("test")
            .handler { client, job ->
                client.newCompleteCommand(job.key)
                    .variables(mapOf("y" to 2))
                    .send()
            }.open()

        // when
        val processInstanceResult = client.newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .variables(mapOf("x" to 1))
            .withResult()
            .send()
            .join()

        // then
        assertThat(processInstanceResult.variablesAsMap)
            .containsEntry("x", 1)
            .containsEntry("y", 2)
    }

}