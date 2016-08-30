package iceburg.soundcontroller;


import com.illposed.osc.OSCMessage;
import com.illposed.osc.OSCPortOut;

import iceburg.events.Event;
import iceburg.events.EventListener;
import iceburg.events.EventType;
import iceburg.events.ProximityEvent;
import iceburg.events.TouchEvent;
import iceburg.events.rabbitmq.RabbitMQEventHub;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;

import java.util.Arrays;
import java.util.List;


/**
 * Controller responsible for interfacing with OSC
 */
public class SoundController {

    private enum ProximityIndicator {OFF, ON};

    private enum TouchSensorIndicator {OFF, ON, CORRESPONDING};

    private static final String EVENTS_EXCHANGE = "events";

    private static final String RABBITMQ_URL = "amqp://localhost";

    private RabbitMQEventHub eventHub;

    private final Logger logger;

    private OSCPortOut sender;

    public SoundController() {
        super();
        this.logger = LoggerFactory.getLogger(this.getClass());
    }

    public void init(String icebergId, String oscHost, Integer oscPort) throws UnknownHostException, SocketException {

        this.logger.debug("Init SoundController: icebergId: {}", icebergId);

        this.sender = new OSCPortOut(InetAddress.getByName(oscHost), oscPort);

        this.eventHub = new RabbitMQEventHub();
        this.eventHub.setRabbitMQUrl(RABBITMQ_URL);
        this.eventHub.setExchangeName(EVENTS_EXCHANGE);
        this.eventHub.init(icebergId);

        this.eventHub.addListener(new EventListener() {

            public List<EventType> getWantedEvents() {
                return Arrays.asList(new EventType[]{EventType.TOUCH, EventType.MULTI_BERG, EventType.PROXIMITY});
            }

            public void handleEvent(final Event e) {

                SoundController.this.logger.debug("Handle event {}", e);

                if (EventType.TOUCH.equals(e.getType()) || EventType.MULTI_BERG.equals(e.getType())) {

                    SoundController.this.handleTouch((TouchEvent) e);

                } else if (EventType.PROXIMITY.equals(e.getType())){

                    SoundController.this.handleProximity((ProximityEvent)e);
                }
            }
        });
    }

    private void handleProximity(ProximityEvent event) {

        Integer proximityIndicator = event.isPersonPresent() ?
                ProximityIndicator.ON.ordinal() : ProximityIndicator.OFF.ordinal();

        sendOSCMessageInResponseToEvent(new OSCMessage("/iceberg/proximity",
                Arrays.asList(new Object[]{proximityIndicator})), event);
    }

    private void handleTouch(TouchEvent event) {

        TouchSensorIndicator touchIndicator = event.isTouched() ? null : TouchSensorIndicator.OFF;

        if (touchIndicator == null) {
            touchIndicator = EventType.MULTI_BERG.equals(event.getType()) ?
                    TouchSensorIndicator.CORRESPONDING : TouchSensorIndicator.ON;
        }

        sendOSCMessageInResponseToEvent(new OSCMessage("/iceberg/sensor",
                Arrays.asList(new Object[]{event.getSensorNumber(), touchIndicator.ordinal()})), event);
    }

    private void sendOSCMessageInResponseToEvent(OSCMessage message, Event event) {

        try {
            this.sender.send(message);
        } catch (IOException ex) {
            this.logger.error("Error sending touch event to OSC:" + event, ex);
        }
    }

    public static void main(String[] args) {

        String message = "java iceburg.soundcontroller.SoundController [iceburg id] [osc host name] [osc port]";

        if (args.length < 3) {
            System.out.println(message);
        }

        SoundController controller = new SoundController();

        try {
            controller.init(args[0], args[1], Integer.getInteger(args[2]));

        } catch (Exception ex) {
            System.out.println(message);
            ex.printStackTrace();
        }
    }
}
