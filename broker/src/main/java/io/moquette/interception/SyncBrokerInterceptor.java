/*
 * Copyright (c) 2012-2018 The original author or authors
 * ------------------------------------------------------
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Apache License v2.0 which accompanies this distribution.
 *
 * The Eclipse Public License is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * The Apache License v2.0 is available at
 * http://www.opensource.org/licenses/apache2.0.php
 *
 * You may elect to redistribute this code under either of these licenses.
 */

package io.moquette.interception;

import io.moquette.broker.subscriptions.Subscription;
import io.moquette.interception.messages.*;
import io.netty.handler.codec.mqtt.MqttConnectMessage;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static io.moquette.logging.LoggingUtils.getInterceptorIds;

/**
 * An interceptor that execute the interception tasks synchronously.
 */
public final class SyncBrokerInterceptor implements Interceptor {

    private static final Logger LOG = LoggerFactory.getLogger(SyncBrokerInterceptor.class);
    private final Map<Class<?>, List<InterceptHandler>> handlers;

    public SyncBrokerInterceptor(List<InterceptHandler> handlers) {
        LOG.info("Initializing broker interceptor. InterceptorIds={}", getInterceptorIds(handlers));
        this.handlers = new HashMap<>();
        for (Class<?> messageType : InterceptHandler.ALL_MESSAGE_TYPES) {
            this.handlers.put(messageType, new CopyOnWriteArrayList<InterceptHandler>());
        }
        for (InterceptHandler handler : handlers) {
            this.addInterceptHandler(handler);
        }
    }

    void stop() {
        LOG.info("interceptors stopped");
    }

    @Override
    public void notifyClientConnected(final MqttConnectMessage msg) {
        for (final InterceptHandler handler : this.handlers.get(InterceptConnectMessage.class)) {
            LOG.debug("Sending MQTT CONNECT message to interceptor. CId={}, interceptorId={}",
                    msg.payload().clientIdentifier(), handler.getID());
            handler.onConnect(new InterceptConnectMessage(msg));
        }
    }

    @Override
    public void notifyClientDisconnected(final String clientID, final String username) {
        for (final InterceptHandler handler : this.handlers.get(InterceptDisconnectMessage.class)) {
            LOG.debug("Notifying MQTT client disconnection to interceptor. CId={}, username={}, interceptorId={}",
                clientID, username, handler.getID());
            handler.onDisconnect(new InterceptDisconnectMessage(clientID, username));
        }
    }

    @Override
    public void notifyClientConnectionLost(final String clientID, final String username) {
        for (final InterceptHandler handler : this.handlers.get(InterceptConnectionLostMessage.class)) {
            LOG.debug("Notifying unexpected MQTT client disconnection to interceptor CId={}, username={}, " +
                "interceptorId={}", clientID, username, handler.getID());
            handler.onConnectionLost(new InterceptConnectionLostMessage(clientID, username));
        }
    }

    @Override
    public void notifyTopicPublished(final MqttPublishMessage msg, final String clientID, final String username) {
        msg.retain();

        try {
            int messageId = msg.variableHeader().messageId();
            String topic = msg.variableHeader().topicName();
            for (InterceptHandler handler : handlers.get(InterceptPublishMessage.class)) {
                LOG.debug("Notifying MQTT PUBLISH message to interceptor. CId={}, messageId={}, topic={}, "
                        + "interceptorId={}", clientID, messageId, topic, handler.getID());
                handler.onPublish(new InterceptPublishMessage(msg, clientID, username));
            }
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void notifyTopicSubscribed(final Subscription sub, final String username) {
        for (final InterceptHandler handler : this.handlers.get(InterceptSubscribeMessage.class)) {
            LOG.debug("Notifying MQTT SUBSCRIBE message to interceptor. CId={}, topicFilter={}, interceptorId={}",
                sub.getClientId(), sub.getTopicFilter(), handler.getID());
            handler.onSubscribe(new InterceptSubscribeMessage(sub, username));
        }
    }

    @Override
    public void notifyTopicUnsubscribed(final String topic, final String clientID, final String username) {
        for (final InterceptHandler handler : this.handlers.get(InterceptUnsubscribeMessage.class)) {
            LOG.debug("Notifying MQTT UNSUBSCRIBE message to interceptor. CId={}, topic={}, interceptorId={}", clientID,
                topic, handler.getID());
            handler.onUnsubscribe(new InterceptUnsubscribeMessage(topic, clientID, username));
        }
    }

    @Override
    public void notifyMessageAcknowledged(final InterceptAcknowledgedMessage msg) {
        for (final InterceptHandler handler : this.handlers.get(InterceptAcknowledgedMessage.class)) {
            LOG.debug("Notifying MQTT ACK message to interceptor. CId={}, messageId={}, topic={}, interceptorId={}",
                msg.getMsg()/*.getClientID()*/, msg.getPacketID(), msg.getTopic(), handler.getID());
            handler.onMessageAcknowledged(msg);
        }
    }

    @Override
    public void addInterceptHandler(InterceptHandler interceptHandler) {
        Class<?>[] interceptedMessageTypes = getInterceptedMessageTypes(interceptHandler);
        LOG.info("Adding MQTT message interceptor. InterceptorId={}, handledMessageTypes={}",
            interceptHandler.getID(), interceptedMessageTypes);
        for (Class<?> interceptMessageType : interceptedMessageTypes) {
            this.handlers.get(interceptMessageType).add(interceptHandler);
        }
    }

    @Override
    public void removeInterceptHandler(InterceptHandler interceptHandler) {
        Class<?>[] interceptedMessageTypes = getInterceptedMessageTypes(interceptHandler);
        LOG.info("Removing MQTT message interceptor. InterceptorId={}, handledMessageTypes={}",
            interceptHandler.getID(), interceptedMessageTypes);
        for (Class<?> interceptMessageType : interceptedMessageTypes) {
            this.handlers.get(interceptMessageType).remove(interceptHandler);
        }
    }

    private static Class<?>[] getInterceptedMessageTypes(InterceptHandler interceptHandler) {
        Class<?>[] interceptedMessageTypes = interceptHandler.getInterceptedMessageTypes();
        if (interceptedMessageTypes == null) {
            return InterceptHandler.ALL_MESSAGE_TYPES;
        }
        return interceptedMessageTypes;
    }
}
