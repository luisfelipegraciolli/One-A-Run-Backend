package dev.shiwa.onearunbackend.config

import com.hivemq.client.mqtt.MqttGlobalPublishFilter
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client
import jakarta.annotation.PreDestroy
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.nio.charset.StandardCharsets
import java.util.UUID

@Configuration
class MqttConfig(
    @Value("\${mqtt.broker-url}") private val brokerUrl: String,
    @Value("\${mqtt.client-id-prefix}") private val clientIdPrefix: String,
    @Value("\${mqtt.mqtt-broker-port}") private val brokerPort: Int,
    @Value("\${mqtt.topic:corrida-app/test/hello}") private val topic: String
) {
    // Guardado para desconectar quando o contexto fechar (evita clientes órfãos nos restarts do DevTools)
    private var asyncClient: Mqtt5AsyncClient? = null

    @Bean
    fun mqttClient(): Mqtt5Client {
        val host = brokerUrl
            .removePrefix("tcp://")
            .removePrefix("ssl://")
            .removePrefix("mqtt://")
            .removePrefix("mqtts://")
            .substringBefore(":")

        // TLS só quando a configuração pedir; em localhost:1883 continua desligado
        val useTls = brokerUrl.startsWith("ssl://") || brokerUrl.startsWith("mqtts://") || brokerPort == 8883

        lateinit var client: Mqtt5AsyncClient

        var builder = Mqtt5Client.builder()
            .identifier(clientIdPrefix + UUID.randomUUID())
            .serverHost(host)
            .serverPort(brokerPort)
            .automaticReconnectWithDefaultConfig()
            .addConnectedListener {
                println("MQTT conectado")
                // Roda a cada (re)conexão, inclusive quando a conexão inicial falhou e a reconexão automática teve sucesso
                subscribeToTestTopic(client)
            }
            .addDisconnectedListener { ctx ->
                System.err.println("MQTT desconectado: ${ctx.cause.message} (fonte: ${ctx.source})")
            }

        if (useTls) builder = builder.sslWithDefaultConfig()

        client = builder.buildAsync()
        asyncClient = client

        // Imprime as mensagens do tópico de teste (assinaturas sem callback próprio)
        client.publishes(MqttGlobalPublishFilter.SUBSCRIBED) { publish ->
            val message = String(publish.payloadAsBytes, StandardCharsets.UTF_8)
            println("Received on ${publish.topic}: $message")
        }

        client.connectWith()
            .cleanStart(true)
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    System.err.println("Initial MQTT connection failed to $host:$brokerPort: ${throwable.message}. Reconnect mechanism will retry in the background.")
                } else {
                    println("Successfully connected to MQTT broker at $host:$brokerPort")
                }
            }

        return client
    }

    private fun subscribeToTestTopic(client: Mqtt5AsyncClient) {
        client.subscribeWith()
            .topicFilter(topic)
            .send()
            .whenComplete { _, throwable ->
                if (throwable != null) {
                    System.err.println("Failed to subscribe to $topic: ${throwable.message}")
                } else {
                    println("Successfully subscribed to topic: $topic")
                }
            }
    }

    @PreDestroy
    fun shutdown() {
        asyncClient?.disconnect()
    }
}