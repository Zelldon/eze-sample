package org.camunda.community.eze

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.model.bpmn.Bpmn
import io.zeebe.containers.ZeebeContainer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers

@Testcontainers
class TestContainerSample {

    @Container
    var zeebe = ZeebeContainer()

    lateinit var client: ZeebeClient

    @BeforeEach
    fun `connect client`() {
        client = ZeebeClient.newClientBuilder()
            .gatewayAddress(zeebe.externalGatewayAddress)
            .usePlaintext()
            .build()
    }

    @Test
    fun `should complete process instance`() {
        // given
        client.newDeployCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .serviceTask("task-1").zeebeJobType("test")
                    .endEvent()
                    .done(),
                "process.bpmn"
            )
            .send()
            .join()

        client.newWorker().jobType("test")
            .handler { client, job ->
                client.newCompleteCommand(job.key).send()
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
    }

}