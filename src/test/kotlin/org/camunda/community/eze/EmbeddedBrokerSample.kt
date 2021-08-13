package org.camunda.community.eze

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.DeploymentEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.test.EmbeddedBrokerRule
import io.camunda.zeebe.test.ZeebeTestRule
import org.assertj.core.api.Assertions
import org.awaitility.kotlin.await
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.Future

class EmbeddedBrokerSample {

    @Rule @JvmField
    public val testRule: ZeebeTestRule = ZeebeTestRule()
    lateinit var client: ZeebeClient

    @Before
    fun `connect client`() {
        client = testRule.client
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
            Assertions.assertThat(deployFuture).isDone
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
        Assertions.assertThat(processInstanceResult.variablesAsMap)
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
        Assertions.assertThat(processInstanceResult.variablesAsMap)
            .containsEntry("x", 1)
            .containsEntry("y", 2)
    }

}
