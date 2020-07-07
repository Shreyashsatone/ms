/*
 * Copyright 2018 - 2022 Anton Tananaev (anton@traccar.org)
 * Copyright 2018 Andrey Kunitsyn (andrey@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.notification;

import com.google.inject.Injector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.Typed;
import org.traccar.notificators.Notificator;
import org.traccar.notificators.NotificatorFirebase;
import org.traccar.notificators.NotificatorMail;
import org.traccar.notificators.NotificatorNull;
import org.traccar.notificators.NotificatorPushover;
import org.traccar.notificators.NotificatorSms;
import org.traccar.notificators.NotificatorTelegram;
import org.traccar.notificators.NotificatorTraccar;
import org.traccar.notificators.NotificatorWeb;
import org.traccar.notificators.NotificatorPushover;
import org.traccar.notificators.NotificatorMqtt;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Singleton
public class NotificatorManager {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificatorManager.class);

    private static final Map<String, Class<? extends Notificator>> NOTIFICATORS_ALL = Map.of(
            "web", NotificatorWeb.class,
            "mail", NotificatorMail.class,
            "sms", NotificatorSms.class,
            "firebase", NotificatorFirebase.class,
            "traccar", NotificatorTraccar.class,
            "telegram", NotificatorTelegram.class,
            "pushover", NotificatorPushover.class);

    private final Injector injector;

<<<<<<< HEAD
    private final Set<String> types = new HashSet<>();

    @Inject
    public NotificatorManager(Injector injector, Config config) {
        this.injector = injector;
        String types = config.getString(Keys.NOTIFICATOR_TYPES);
        if (types != null) {
            this.types.addAll(Arrays.asList(types.split(",")));
=======
    public NotificatorManager() {
        final String[] types = Context.getConfig().getString("notificator.types", "").split(",");
        for (String type : types) {
            String defaultNotificator = "";
            switch (type) {
                case "web":
                    defaultNotificator = NotificatorWeb.class.getCanonicalName();
                    break;
                case "mail":
                    defaultNotificator = NotificatorMail.class.getCanonicalName();
                    break;
                case "sms":
                    defaultNotificator = NotificatorSms.class.getCanonicalName();
                    break;
                case "firebase":
                    defaultNotificator = NotificatorFirebase.class.getCanonicalName();
                    break;
                case "telegram":
                    defaultNotificator = NotificatorTelegram.class.getCanonicalName();
                    break;
                case "pushover":
                    defaultNotificator = NotificatorPushover.class.getCanonicalName();
                    break;
                case "mqtt":
                    defaultNotificator = NotificatorMqtt.class.getCanonicalName();
                    break;
                default:
                    break;
            }
            final String className = Context.getConfig()
                    .getString("notificator." + type + ".class", defaultNotificator);
            try {
                notificators.put(type, (Notificator) Class.forName(className).newInstance());
            } catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
                LOGGER.warn("Unable to load notificator class for " + type + " " + className + " " + e.getMessage());
            }
>>>>>>> Added MQTT dependency and notificator class
        }
    }

    public Notificator getNotificator(String type) {
        var clazz = NOTIFICATORS_ALL.get(type);
        if (clazz != null) {
            var notificator = injector.getInstance(clazz);
            if (notificator != null) {
                return notificator;
            }
        }
        LOGGER.warn("Failed to get notificator {}", type);
        return new NotificatorNull();
    }

    public Set<Typed> getAllNotificatorTypes() {
        return types.stream().map(Typed::new).collect(Collectors.toUnmodifiableSet());
    }

}
