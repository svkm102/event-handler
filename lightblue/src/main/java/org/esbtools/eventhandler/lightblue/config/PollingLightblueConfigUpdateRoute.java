/*
 *  Copyright 2016 esbtools Contributors and/or its affiliates.
 *
 *  This file is part of esbtools.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.esbtools.eventhandler.lightblue.config;

import org.esbtools.eventhandler.lightblue.client.FindRequests;

import com.redhat.lightblue.client.LightblueClient;
import com.redhat.lightblue.client.request.data.DataFindRequest;
import org.apache.camel.builder.RouteBuilder;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Updates configuration from stored values in a lightblue instance.
 */
public class PollingLightblueConfigUpdateRoute extends RouteBuilder {
    private final String configDomain;
    private final Duration pollingInterval;
    private final LightblueClient lightblue;
    private final MutableLightblueNotificationRepositoryConfig notificationRepositoryConfig;
    private final MutableLightblueDocumentEventRepositoryConfig documentEventRepositoryConfig;

    private final DataFindRequest findConfig;

    private static final AtomicInteger idCounter = new AtomicInteger(1);
    private final int id = idCounter.getAndIncrement();

    /**
     * @param configDomain See {@link EventHandlerConfigEntity#setDomain(String)}}. This is whatever
     *                     you use when you persist your configuration.
     * @param pollingInterval How often to poll for configuration data stored in lightblue.
     * @param lightblue A lightblue client configured to talk to lightblue
     * @param notificationRepositoryConfig A thread-safe config object to update
     * @param documentEventRepositoryConfig A thread-safe config object to update
     */
    public PollingLightblueConfigUpdateRoute(String configDomain, Duration pollingInterval,
            LightblueClient lightblue,
            MutableLightblueNotificationRepositoryConfig notificationRepositoryConfig,
            MutableLightblueDocumentEventRepositoryConfig documentEventRepositoryConfig) {
        this.pollingInterval = Objects.requireNonNull(pollingInterval, "pollingInterval");
        this.lightblue = Objects.requireNonNull(lightblue, "lightblue");
        this.notificationRepositoryConfig = Objects.requireNonNull(notificationRepositoryConfig,
                "notificationRepositoryConfig");
        this.documentEventRepositoryConfig = Objects.requireNonNull(documentEventRepositoryConfig,
                "documentEventRepositoryConfig");
        this.configDomain = Objects.requireNonNull(configDomain, "configDomain");

        findConfig = FindRequests.eventHandlerConfigForDomain(configDomain);
    }

    @Override
    public void configure() throws Exception {
        from("timer:pollForEventHandlerConfigUpdates" + id + "?period=" + pollingInterval.toMillis())
        .routeId("eventHandlerConfigUpdater-" + id)
        .process(exchange -> {
            EventHandlerConfigEntity storedConfig =
                    lightblue.data(findConfig, EventHandlerConfigEntity.class);

            if (storedConfig == null) {
                log.info("No event handler config found for domain: {}", configDomain);
                return;
            }

            Set<String> canonicalTypes = storedConfig.getCanonicalTypesToProcess();
            if (canonicalTypes != null) {
                documentEventRepositoryConfig.setCanonicalTypesToProcess(canonicalTypes);
            }

            Set<String> entityNames = storedConfig.getEntityNamesToProcess();
            if (entityNames != null) {
                notificationRepositoryConfig.setEntityNamesToProcess(entityNames);
            }

            Integer documentEventBatchSize = storedConfig.getDocumentEventsBatchSize();
            if (documentEventBatchSize != null) {
                documentEventRepositoryConfig.setDocumentEventsBatchSize(documentEventBatchSize);
            }

            Duration notificationProcessingTimeout = storedConfig
                    .getNotificationProcessingTimeout();
            if (notificationProcessingTimeout != null) {
                notificationRepositoryConfig
                        .setNotificationProcessingTimeout(notificationProcessingTimeout);
            }

            Duration notificationExpireThreshold = storedConfig
                    .getNotificationExpireThreshold();
            if (notificationExpireThreshold!= null) {
                notificationRepositoryConfig
                        .setNotificationExpireThreshold(notificationExpireThreshold);
            }

            Duration documentEventProcessingTimeout = storedConfig
                    .getDocumentEventProcessingTimeout();
            if (documentEventProcessingTimeout != null) {
                documentEventRepositoryConfig
                        .setDocumentEventProcessingTimeout(documentEventProcessingTimeout);
            }

            Duration documentEventExpireThreshold = storedConfig
                    .getDocumentEventExpireThreshold();
            if (documentEventExpireThreshold!= null) {
                documentEventRepositoryConfig
                        .setDocumentEventExpireThreshold(documentEventExpireThreshold);
            }

            Optional<Integer> maxDocumentEventsPerInsert = storedConfig
                    .getOptionalMaxDocumentEventsPerInsert();
            documentEventRepositoryConfig.setMaxDocumentEventsPerInsert(maxDocumentEventsPerInsert);
        });
    }
}
