package com.ascargon.rocketshow.api;

import com.ascargon.rocketshow.PlayerService;
import com.ascargon.rocketshow.composition.SetService;
import com.ascargon.rocketshow.midi.MidiSignal;
import com.ascargon.rocketshow.util.UpdateService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import javax.annotation.PreDestroy;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Notify all connected websocket clients about the current device state.
 */
@Service
public class DefaultNotificationService extends TextWebSocketHandler implements NotificationService {

    private final StateService stateService;

    private final List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    private boolean midiLearn = false;

    public DefaultNotificationService(StateService stateService) {
        this.stateService = stateService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    private void notifyClients(PlayerService playerService, SetService setService, MidiSignal midiSignal, UpdateService.UpdateState updateState, Boolean isUpdateFinished) throws IOException {
        State currentState = stateService.getCurrentState(playerService, setService);
        currentState.setMidiSignal(midiSignal);
        currentState.setUpdateState(updateState);
        currentState.setUpdateFinished(isUpdateFinished);

        ObjectMapper mapper = new ObjectMapper();
        String returnValue = mapper.writeValueAsString(currentState);

        for (WebSocketSession webSocketSession : sessions) webSocketSession.sendMessage(new TextMessage(returnValue));
    }

    // Notify the clients about the current state and include a midi signal, if
    // midi learn is activated
    @Override
    public void notifyClients(MidiSignal midiSignal) throws IOException {
        notifyClients(null, null, midiSignal, null, null);
    }

    // Notify the clients about the current state and include update
    // information, if an update is running
    @Override
    public void notifyClients(UpdateService.UpdateState updateState) throws IOException {
        notifyClients(null, null, null, updateState, null);
    }

    @Override
    public void notifyClients(PlayerService playerService) throws IOException {
        notifyClients(playerService, null, null, null, null);
    }

    @Override
    public void notifyClients(SetService setService) throws IOException {
        notifyClients(null, setService, null, null, null);
    }

    @Override
    public void notifyClients(boolean isUpdateFinished) throws IOException {
        notifyClients(null, null, null, null, isUpdateFinished);
    }

    @Override
    public void notifyClients() throws IOException {
        notifyClients(null, null, null, null, null);
    }

    @Override
    public boolean isMidiLearn() {
        return midiLearn;
    }

    @Override
    public void setMidiLearn(boolean midiLearn) {
        this.midiLearn = midiLearn;
    }

    @PreDestroy
    public void close() throws IOException {
        for (WebSocketSession webSocketSession : sessions) webSocketSession.close();
    }

}
