/**
 * Copyright (c) 2010-2020 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.openhab.ui.habot.rest.internal;

import java.security.InvalidParameterException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.openhab.core.auth.Role;
import org.openhab.core.io.rest.LocaleService;
import org.openhab.core.io.rest.RESTConstants;
import org.openhab.core.io.rest.RESTResource;
import org.openhab.core.voice.VoiceManager;
import org.openhab.core.voice.text.InterpretationException;
import org.openhab.ui.habot.card.Card;
import org.openhab.ui.habot.card.internal.CardRegistry;
import org.openhab.ui.habot.nlp.ChatReply;
import org.openhab.ui.habot.nlp.ItemNamedAttribute;
import org.openhab.ui.habot.nlp.ItemResolver;
import org.openhab.ui.habot.nlp.internal.AnswerFormatter;
import org.openhab.ui.habot.nlp.internal.OpenNLPInterpreter;
import org.openhab.ui.habot.notification.internal.NotificationService;
import org.openhab.ui.habot.notification.internal.webpush.Subscription;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.jaxrs.whiteboard.JaxrsWhiteboardConstants;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JSONRequired;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsApplicationSelect;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsName;
import org.osgi.service.jaxrs.whiteboard.propertytypes.JaxrsResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;

/**
 * This class describes the /habot resource of the REST API.
 *
 * @author Yannick Schaus - Initial contribution
 * @author Wouter Born - Migrated to JAX-RS Whiteboard Specification
 */
@Component
@JaxrsResource
@JaxrsName(HABotResource.PATH_HABOT)
@JaxrsApplicationSelect("(" + JaxrsWhiteboardConstants.JAX_RS_NAME + "=" + RESTConstants.JAX_RS_NAME + ")")
@JSONRequired
@RolesAllowed({ Role.USER, Role.ADMIN })
@Path(HABotResource.PATH_HABOT)
@Api(HABotResource.PATH_HABOT)
@NonNullByDefault
public class HABotResource implements RESTResource {

    /** The URI path to this resource */
    public static final String PATH_HABOT = "habot";

    private static final String OPENNLP_HLI = "opennlp";

    private final Logger logger = LoggerFactory.getLogger(HABotResource.class);

    private final CardRegistry cardRegistry;
    private final ItemResolver itemResolver;
    private final LocaleService localeService;
    private final NotificationService notificationService;
    private final VoiceManager voiceManager;

    @Activate
    public HABotResource( //
            final @Reference CardRegistry cardRegistry, //
            final @Reference ItemResolver itemResolver, //
            final @Reference LocaleService localeService, //
            final @Reference NotificationService notificationService, //
            final @Reference VoiceManager voiceManager) {
        this.cardRegistry = cardRegistry;
        this.itemResolver = itemResolver;
        this.localeService = localeService;
        this.notificationService = notificationService;
        this.voiceManager = voiceManager;
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/greet")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Retrieves a first greeting message from the bot in the specified or configured language.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK", response = ChatReply.class),
            @ApiResponse(code = 500, message = "There is no support for the configured language") })
    public Response greet() {
        final Locale locale = localeService.getLocale(null);

        AnswerFormatter answerFormatter = new AnswerFormatter(locale);

        String greeting = answerFormatter.getRandomAnswer("greeting");
        ChatReply reply = new ChatReply(locale);
        reply.setAnswer(greeting);

        return Response.ok(reply).build();
    }

    @POST
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/chat")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Send a query to HABot to interpret.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK", response = ChatReply.class),
            @ApiResponse(code = 500, message = "An interpretation error occurred") })
    public Response chat(@ApiParam(value = "human language query", required = true) String query) throws Exception {
        final Locale locale = localeService.getLocale(null);

        // interpret
        OpenNLPInterpreter hli = (OpenNLPInterpreter) voiceManager.getHLI(OPENNLP_HLI);
        if (hli == null) {
            throw new InterpretationException("The OpenNLP interpreter is not available");
        }
        ChatReply reply = hli.reply(locale, query);

        return Response.ok(reply).build();
    }

    @GET
    @RolesAllowed({ Role.USER, Role.ADMIN })
    @Path("/attributes")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Gets all item named attributes.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK", response = ChatReply.class),
            @ApiResponse(code = 500, message = "An error occurred") })
    public Response getAttributes() throws Exception {
        final Locale locale = localeService.getLocale(null);

        itemResolver.setLocale(locale);
        Map<String, Set<ItemNamedAttribute>> attributesByItemName = new HashMap<>();
        itemResolver.getAllItemNamedAttributes().entrySet().stream()
                .forEach(entry -> attributesByItemName.put(entry.getKey().getName(), entry.getValue()));

        return Response.ok(attributesByItemName).build();
    }

    @POST
    @Path("/notifications/subscribe")
    // TEXT_PLAIN to work around https://github.com/openhab/openhab-cloud/issues/31
    @Consumes(MediaType.TEXT_PLAIN) // @Consumes(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Subscribes a new client for push notifications.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response webPushSubscribe(String subscriptionJson) throws Exception {
        Gson gson = new Gson();
        Subscription subscription = gson.fromJson(subscriptionJson, Subscription.class);
        notificationService.addSubscription(subscription);

        // send a test notification to the client
        Response testNotificationResponse = notificationService.sendNotification(subscription,
                "{ \"body\": \"Congratulations, push notifications are working properly! 🎉👌\" }").get();

        logger.debug("Test notification response: {}", testNotificationResponse.toString());
        logger.debug("Test notification response headers: {}", testNotificationResponse.getStringHeaders());
        logger.debug("Test notification response body: {}", testNotificationResponse.readEntity(String.class));

        return Response.ok(testNotificationResponse.getStatusInfo()).build();
    }

    @GET
    @Path("/notifications/vapid")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Gets or generates the public VAPID key used for push notifications.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response webPushConfig() throws Exception {
        String publicVAPIDKey = notificationService.getVAPIDPublicKey();

        return Response.ok(publicVAPIDKey).build();
    }

    @GET
    @Path("/cards")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Gets all cards of the card deck.", response = Card.class, responseContainer = "List")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response getAllCards() {
        Collection<Card> cards = cardRegistry.getNonEphemeral();

        return Response.ok(cards).build();
    }

    @GET
    @Path("/cards/{cardUID}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Gets a card from the card deck by its UID.", response = Card.class)
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "The card with the provided UID doesn't exist"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response getCardByUid(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID) {
        Card card = cardRegistry.get(cardUID);
        if (card == null) {
            return Response.status(Status.NOT_FOUND).build();
        }

        return Response.ok(card).build();
    }

    @POST
    @Path("/cards")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Creates a new card in the card deck.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "The card was created"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response createCard(@ApiParam(value = "card", required = true) Card card) {
        card.updateTimestamp();
        card.setEphemeral(false);
        Card existingCard = cardRegistry.get(card.getUID());
        if (existingCard != null && existingCard.isEphemeral()) {
            cardRegistry.remove(card.getUID());
        }
        Card createdCard = cardRegistry.add(card);

        return Response.ok(createdCard).build();
    }

    @PUT
    @Path("/cards/{cardUID}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Updates a card in the card deck.")
    public Response updateCard(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID,
            @ApiParam(value = "card", required = true) Card card) {
        if (!card.getUID().equals(cardUID)) {
            throw new InvalidParameterException(
                    "The card UID in the body of the request should match the UID in the URL");
        }
        card.updateTimestamp();
        Card updatedCard = cardRegistry.update(card);

        return Response.ok(updatedCard).build();
    }

    @DELETE
    @Path("/cards/{cardUID}")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Deletes a card from the card deck.")
    public Response deleteCard(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID) {
        cardRegistry.remove(cardUID);

        return Response.ok().build();
    }

    @PUT
    @Path("/cards/{cardUID}/bookmark")
    @ApiOperation(value = "Sets a bookmark on a card.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "The card with the provided UID doesn't exist"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response setCardBookmark(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID) {
        Card card = cardRegistry.get(cardUID);
        if (card == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        card.setBookmark(true);
        cardRegistry.update(card);

        return Response.ok().build();
    }

    @GET
    @Path("/cards/recent")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Creates a new card in the card deck.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "The card was created"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response createCard(@QueryParam(value = "skip") int skip, @QueryParam(value = "count") int count) {
        Collection<Card> cards = cardRegistry.getRecent(skip, count);

        return Response.ok(cards).build();
    }

    @DELETE
    @Path("/cards/{cardUID}/bookmark")
    @ApiOperation(value = "Removes the bookmark on a card.")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "The card with the provided UID doesn't exist"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response unsetCardBookmark(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID) {
        Card card = cardRegistry.get(cardUID);
        if (card == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        card.setBookmark(false);
        cardRegistry.update(card);

        return Response.ok().build();
    }

    @PUT
    @Path("/cards/{cardUID}/timestamp")
    @ApiOperation(value = "Updates the timestamp on a card to the current time")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "The card with the provided UID doesn't exist"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response updateCardTimestamp(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID) {
        Card card = cardRegistry.get(cardUID);
        if (card == null) {
            return Response.status(Status.NOT_FOUND).build();
        }
        card.updateTimestamp();
        cardRegistry.update(card);

        return Response.ok().build();
    }

    /*
     * The following are compatibility endpoints to work around (temporary) issues with home.myopenhab.org
     */

    @POST
    @Path("/compat/cards")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Creates a new card in the card deck (compatibility endpoint).")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "The card was created"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response createCard(@ApiParam(value = "card", required = true) String card) {
        Gson gson = new Gson();
        return createCard(gson.fromJson(card, Card.class));
    }

    @POST
    @Path("/compat/cards/{cardUID}")
    @Consumes(MediaType.TEXT_PLAIN)
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Updates a card in the card deck (compatibility endpoint).")
    public Response updateCard(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID,
            @ApiParam(value = "card", required = true) String card) {
        Gson gson = new Gson();
        return updateCard(cardUID, gson.fromJson(card, Card.class));
    }

    @POST
    @Path("/compat/cards/{cardUID}/delete")
    @Produces(MediaType.APPLICATION_JSON)
    @ApiOperation(value = "Deletes a card from the card deck (compatibility endpoint).")
    public Response deleteCardPost(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID) {
        return deleteCard(cardUID);
    }

    @POST
    @Path("/compat/cards/{cardUID}/unbookmark")
    @ApiOperation(value = "Removes the bookmark on a card (compatibility endpoint).")
    @ApiResponses(value = { @ApiResponse(code = 200, message = "OK"),
            @ApiResponse(code = 404, message = "The card with the provided UID doesn't exist"),
            @ApiResponse(code = 500, message = "An error occured") })
    public Response unsetCardBookmarkCompat(@PathParam("cardUID") @ApiParam(value = "cardUID") String cardUID) {
        return unsetCardBookmark(cardUID);
    }
}
