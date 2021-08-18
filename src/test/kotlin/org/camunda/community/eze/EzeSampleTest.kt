package org.camunda.community.eze

import io.camunda.zeebe.client.ZeebeClient
import io.camunda.zeebe.client.api.response.DeploymentEvent
import io.camunda.zeebe.model.bpmn.Bpmn
import io.camunda.zeebe.protocol.record.intent.TimerIntent
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.camunda.community.eze.RecordStream.withIntent
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.concurrent.Future

@EmbeddedZeebeEngine
class EzeSampleTest {

    lateinit var client: ZeebeClient
    lateinit var recordStream: RecordStreamSource
    lateinit var clock: ZeebeEngineClock

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

    @Test
    fun `should inject clock`() {
        // given
        client.newDeployCommand()
            .addProcessModel(
                Bpmn.createExecutableProcess("process")
                    .startEvent()
                    .intermediateCatchEvent()
                    .timerWithDuration("PT1H")
                    .zeebeOutputExpression("true", "done")
                    .endEvent()
                    .done(), "process.bpmn"
            )
            .send()
            .join()

        val processInstanceResult = client.newCreateInstanceCommand()
            .bpmnProcessId("process")
            .latestVersion()
            .withResult()
            .send()

        await.untilAsserted {
            val timerCreated = recordStream
                .timerRecords()
                .withIntent(TimerIntent.CREATED)
                .firstOrNull()

            assertThat(timerCreated).isNotNull
        }

        // when
        clock.increaseTime(Duration.ofDays(1))

        // then
        assertThat(processInstanceResult.join().variablesAsMap)
            .containsEntry("done", true)
    }

}
