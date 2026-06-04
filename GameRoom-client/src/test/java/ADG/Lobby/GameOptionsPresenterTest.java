package ADG.Lobby;

import ADG.PresenterManager;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.user.client.Window;
import com.google.gwt.user.client.ui.Button;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GameOptionsPresenterTest {

    @Mock GameOptionsView    view;
    @Mock PresenterManager   presenterManager;
    @Mock RoomServiceAsync   roomService;
    @Mock Button             confirmButton;
    @Mock Button             cancelButton;

    private GameOptionsPresenter presenter;
    private Room room;
    private ClickHandler confirmClickHandler;

    @BeforeEach
    void setUp() {
        room = new Room();
        room.setMinPlayers(2);
        room.setMaxPlayers(8);

        when(view.getConfirmButton()).thenReturn(confirmButton);
        when(view.getCancelButton()).thenReturn(cancelButton);

        presenter = new GameOptionsPresenter(view, room, presenterManager, roomService, false);
        presenter.start();

        ArgumentCaptor<ClickHandler> captor = ArgumentCaptor.forClass(ClickHandler.class);
        verify(confirmButton).addClickHandler(captor.capture());
        confirmClickHandler = captor.getValue();
    }

    private void clickConfirm() {
        confirmClickHandler.onClick(null);
    }

    // ── valid inputs ─────────────────────────────────────────────────────────

    @Test
    void confirm_withMaxAtUpperBound_setsMaxPlayersAndCallsUpdateRoom() {
        when(view.getMaxPlayers()).thenReturn(8);
        when(view.getMinBound()).thenReturn(2);
        when(view.getMaxBound()).thenReturn(8);

        clickConfirm();

        assertEquals(8, room.getMaxPlayers());
        verify(roomService).updateRoom(eq(room), any());
    }

    @Test
    void confirm_withMaxAtLowerBound_setsMaxPlayersAndCallsUpdateRoom() {
        when(view.getMaxPlayers()).thenReturn(2);
        when(view.getMinBound()).thenReturn(2);
        when(view.getMaxBound()).thenReturn(8);

        clickConfirm();

        assertEquals(2, room.getMaxPlayers());
        verify(roomService).updateRoom(eq(room), any());
    }

    @Test
    void confirm_withMaxInRange_setsMaxPlayersAndCallsUpdateRoom() {
        when(view.getMaxPlayers()).thenReturn(5);
        when(view.getMinBound()).thenReturn(2);
        when(view.getMaxBound()).thenReturn(8);

        clickConfirm();

        assertEquals(5, room.getMaxPlayers());
        verify(roomService).updateRoom(eq(room), any());
    }

    // ── invalid inputs ───────────────────────────────────────────────────────

    @Test
    void confirm_withMaxBelowMin_alertsAndDoesNotCallUpdateRoom() {
        when(view.getMaxPlayers()).thenReturn(1);
        when(view.getMinBound()).thenReturn(2);
        when(view.getMaxBound()).thenReturn(8);

        try (MockedStatic<Window> w = mockStatic(Window.class)) {
            clickConfirm();

            w.verify(() -> Window.alert(contains("2")));
            verify(roomService, never()).updateRoom(any(), any());
        }
    }

    @Test
    void confirm_withMaxAboveHardMax_alertsAndDoesNotCallUpdateRoom() {
        when(view.getMaxPlayers()).thenReturn(9);
        when(view.getMinBound()).thenReturn(2);
        when(view.getMaxBound()).thenReturn(8);

        try (MockedStatic<Window> w = mockStatic(Window.class)) {
            clickConfirm();

            w.verify(() -> Window.alert(contains("8")));
            verify(roomService, never()).updateRoom(any(), any());
        }
    }

    @Test
    void confirm_withNonNumericInput_doesNotCallUpdateRoom() {
        // getMaxPlayers() returns -1 when the text box cannot be parsed
        when(view.getMaxPlayers()).thenReturn(-1);
        when(view.getMinBound()).thenReturn(2);
        when(view.getMaxBound()).thenReturn(8);

        try (MockedStatic<Window> w = mockStatic(Window.class)) {
            clickConfirm();

            verify(roomService, never()).updateRoom(any(), any());
        }
    }

    // ── fixed-size games (min == max) ─────────────────────────────────────────

    @Test
    void confirm_withFixedPlayerCount_setsExactValueAndCallsUpdateRoom() {
        // e.g. Battleships: exactly 2 players, min == max
        when(view.getMaxPlayers()).thenReturn(2);
        when(view.getMinBound()).thenReturn(2);
        when(view.getMaxBound()).thenReturn(2);

        clickConfirm();

        assertEquals(2, room.getMaxPlayers());
        verify(roomService).updateRoom(eq(room), any());
    }
}