package dev.shiwa.onearunbackend.service

import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5PublishResult
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.util.concurrent.CompletableFuture

@Service
class MqttService(
    private val mqttClient: Mqtt5Client,
    @Value("\${mqtt.topic:corrida-app/test/hello}") private val defaultTopic: String = "corrida-app/test/hello"
) {
    fun publish(topic: String = defaultTopic, payload: String = "Hello from the backend!"): CompletableFuture<Mqtt5PublishResult> {
        val publishMessage = Mqtt5Publish.builder()
            .topic(topic)
            .payload(payload.toByteArray(StandardCharsets.UTF_8))
            .build()
        return mqttClient.toAsync().publish(publishMessage)
    }

    fun subscribe(topic: String = defaultTopic): CompletableFuture<Mqtt5SubAck> {
        val subscribeMessage = Mqtt5Subscribe.builder()
            .topicFilter(topic)
            .build()
        return mqttClient.toAsync().subscribe(subscribeMessage)
    }

    fun subscribe(topic: String, callback: (topic: String, payload: String) -> Unit): CompletableFuture<Mqtt5SubAck> {
        return mqttClient.toAsync().subscribeWith()
            .topicFilter(topic)
            .callback { publish ->
                callback(publish.topic.toString(), String(publish.payloadAsBytes, StandardCharsets.UTF_8))
            }
            .send()
    }
}
