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

package org.esbtools.eventhandler.lightblue.client;

import org.esbtools.eventhandler.lightblue.DocumentEventEntity;
import org.esbtools.lightbluenotificationhook.NotificationEntity;

import com.redhat.lightblue.client.Literal;
import com.redhat.lightblue.client.Query;
import com.redhat.lightblue.client.Query.BinOp;
import com.redhat.lightblue.client.Update;
import com.redhat.lightblue.client.request.data.DataUpdateRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public abstract class UpdateRequests {
    private static Logger logger = LoggerFactory.getLogger(UpdateRequests.class);

    public static Collection<DataUpdateRequest> notificationsStatusAndProcessedDate(
            Collection<NotificationEntity> updatedNotificationEntities) {
        List<DataUpdateRequest> requests = new ArrayList<>(updatedNotificationEntities.size());

        for (NotificationEntity entity : updatedNotificationEntities) {
            DataUpdateRequest request = new DataUpdateRequest(
                    NotificationEntity.ENTITY_NAME,
                    NotificationEntity.ENTITY_VERSION);

            if (entity.get_id() == null) {
                logger.warn("Tried to update an entity's status and processed date, but entity " +
                        "has no id. Entity was: " + entity);
                continue;
            }

            request.where(Query.withValue("_id", BinOp.eq, entity.get_id()));

            List<Update> updates = new ArrayList<>(2);
            updates.add(Update.set("status", entity.getStatus().toString()));

            Date processedDate = entity.getProcessedDate();

            if (processedDate != null) {
                updates.add(Update.set("processedDate", processedDate));
            }

            // Work around client bug.
            request.updates(updates.toArray(new Update[updates.size()]));

            requests.add(request);
        }

        return requests;
    }

    /** "Status" here means status and corresponding date(s) to go along with it. */
    public static DataUpdateRequest notificationStatusIfCurrent(NotificationEntity entity,
            @Nullable Date originalProcessingDate) {
        DataUpdateRequest request = new DataUpdateRequest(
                NotificationEntity.ENTITY_NAME,
                NotificationEntity.ENTITY_VERSION);

        request.where(Query.and(
                Query.withValue("_id", BinOp.eq, entity.get_id()),
                Query.withValue("processingDate", BinOp.eq, originalProcessingDate)
        ));

        List<Update> setStatusAndDates = new ArrayList<>(3);
        setStatusAndDates.add(Update.set("processingDate", entity.getProcessingDate()));
        setStatusAndDates.add(Update.set("status", entity.getStatus().toString()));

        if (entity.getProcessedDate() != null){
            setStatusAndDates.add(Update.set("processedDate", entity.getProcessedDate()));
        }

        request.updates(setStatusAndDates);

        return request;
    }

    public static Collection<DataUpdateRequest> documentEventsStatusAndProcessedDate(
            Collection<DocumentEventEntity> updatedEventEntities) {
        List<DataUpdateRequest> requests = new ArrayList<>(updatedEventEntities.size());

        for (DocumentEventEntity entity : updatedEventEntities) {
            DataUpdateRequest request = new DataUpdateRequest(
                    DocumentEventEntity.ENTITY_NAME,
                    DocumentEventEntity.VERSION);

            if (entity.get_id() == null) {
                logger.warn("Tried to update an entity's status and processed date, but entity " +
                        "has no id. Entity was: " + entity);
                continue;
            }

            request.where(Query.withValue("_id", BinOp.eq, entity.get_id()));

            List<Update> updates = new ArrayList<>(2);
            updates.add(Update.set("status", entity.getStatus().toString()));

            ZonedDateTime processedDate = entity.getProcessedDate();

            if (processedDate != null) {
                updates.add(Update.set("processedDate", Date.from(processedDate.toInstant())));
            }

            // Work around client bug.
            // https://github.com/lightblue-platform/lightblue-client/issues/225
            request.updates(updates.toArray(new Update[updates.size()]));

            requests.add(request);
        }

        return requests;
    }

    /** "Status" here means status and corresponding date(s) to go along with it. */
    public static DataUpdateRequest documentEventStatusDatesAndSurvivorOfIfCurrent(DocumentEventEntity entity,
            @Nullable ZonedDateTime originalProcessingDate) {
        DataUpdateRequest request = new DataUpdateRequest(
                DocumentEventEntity.ENTITY_NAME,
                DocumentEventEntity.VERSION);

        List<Query> idStatusAndDateMatch = new ArrayList<>();
        List<Update> updateStatusDateAndSurvivorOf = new ArrayList<>(2);

        ZonedDateTime processedDate = entity.getProcessedDate();

        idStatusAndDateMatch.add(Query.withValue("_id", BinOp.eq, entity.get_id()));

        if (originalProcessingDate != null) {
            idStatusAndDateMatch.add(Query.withValue(
                    "processingDate", BinOp.eq,
                    Date.from(originalProcessingDate.toInstant())));

            // We don't care if original event was processing or unprocessed. Unprocessed happens
            // when event is manually unprocessed. Matching timestamp still ensures we prevent
            // double processing.
            idStatusAndDateMatch.add(Query.withValues("status", Query.NaryOp.in, Literal.values(
                    DocumentEventEntity.Status.processing.toString(),
                    DocumentEventEntity.Status.unprocessed.toString())));
        } else {
            idStatusAndDateMatch.add(
                    Query.withValue("processingDate", BinOp.eq, Literal.value(null)));
            idStatusAndDateMatch.add(
                    Query.withValue("status", BinOp.eq, DocumentEventEntity.Status.unprocessed.toString()));
        }

        if (processedDate != null) {
            updateStatusDateAndSurvivorOf.add(
                    Update.set("processedDate", Date.from(processedDate.toInstant())));
        }

        updateStatusDateAndSurvivorOf.add(
                Update.set("status", entity.getStatus().toString()));
        updateStatusDateAndSurvivorOf.add(
                Update.set("processingDate", Date.from(entity.getProcessingDate().toInstant())));

        if (entity.getSurvivorOfIds() != null) {
            String[] survivorOfIds = entity.getSurvivorOfIds().stream().toArray(String[]::new);
            updateStatusDateAndSurvivorOf.add(Update.set("survivorOfIds",
                    // https://github.com/lightblue-platform/lightblue-client/issues/289
                    Literal.value(Literal.toJson(Literal.values(survivorOfIds)))));
        }

        request.where(Query.and(idStatusAndDateMatch));
        request.updates(updateStatusDateAndSurvivorOf);

        return request;
    }
}
