package co.medistock.catalog.adapters;

import co.medistock.catalog.core.*;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class JpaMedicationsTest {
    private final MedicationRepository rows = mock(MedicationRepository.class);
    private final CreationRepository intents = mock(CreationRepository.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final JpaMedications store = new JpaMedications(rows, intents, jdbc);
    private final UUID actor = UUID.randomUUID();
    private final NewMedication request = new NewMedication("MED-1", "Nombre", "Principio", "Tableta", "10 mg", "Unidad");

    private MedicationEntity locked() {
        var row = new MedicationEntity(request, actor);
        when(rows.locked(row.id)).thenReturn(Optional.of(row));
        return row;
    }

    private CreationEntity createdIntent() {
        when(intents.findByActorAndRequestKey(actor, "request-123")).thenReturn(Optional.empty());
        store.create(actor, "request-123", request);
        var captured = ArgumentCaptor.forClass(CreationEntity.class);
        verify(intents).saveAndFlush(captured.capture());
        when(intents.findByActorAndRequestKey(actor, "request-123")).thenReturn(Optional.of(captured.getValue()));
        return captured.getValue();
    }

    @Test void replayUsesOriginalSnapshotInsteadOfCurrentEntity() {
        var intent = createdIntent();
        var replay = store.create(actor, "request-123", request);
        assertFalse(replay.created());
        assertEquals(intent.medicationId, replay.medication().id());
        assertEquals("Nombre", replay.medication().name());
        verify(rows, times(1)).saveAndFlush(any(MedicationEntity.class));
        verifyNoMoreInteractions(rows);
    }

    @Test void reusedKeyWithDifferentDataIsConflict() {
        createdIntent();
        var other = new NewMedication("MED-1", "Otro", "Principio", "Tableta", "10 mg", "Unidad");
        assertEquals(409, assertThrows(CatalogFailure.class,
            () -> store.create(actor, "request-123", other)).status());
        verify(intents, times(1)).saveAndFlush(any(CreationEntity.class));
    }

    @Test void staleVersionRejectsBeforeAnyWrite() {
        var row = locked(); row.version = 2;
        assertEquals(412, assertThrows(CatalogFailure.class,
            () -> store.update(actor, row.id, 1, request)).status());
        verifyNoInteractions(jdbc);
    }

    @Test void unitCannotChange() {
        var row = locked();
        var other = new NewMedication("MED-1", "Nombre", "Principio", "Tableta", "10 mg", "Caja");
        assertEquals(422, assertThrows(CatalogFailure.class,
            () -> store.update(actor, row.id, 0, other)).status());
        verifyNoInteractions(jdbc);
    }

    @Test void equalUpdateDoesNotAdvanceVersionOrAudit() {
        var row = locked(); row.version = 3;
        assertEquals(3, store.update(actor, row.id, 3, request).version());
        verifyNoInteractions(jdbc);
    }

    @Test void repeatedDeactivationDoesNotWriteAgain() {
        var row = locked(); row.active = false;
        assertFalse(store.deactivate(actor, row.id, 0).medication().active());
        verifyNoInteractions(jdbc);
    }

    @Test void maintenanceWritesOnlyMutableFieldsAndPreservesCreation() {
        var row = locked();
        var other = new NewMedication("MED-2", "Otro", "Principio", "Tableta", "10 mg", "Unidad");
        var result = store.update(actor, row.id, 0, other);
        assertEquals(1, result.version());
        assertEquals(row.id, result.medication().id());
        assertEquals(row.createdAt, result.medication().createdAt());
        assertEquals("Unidad", result.medication().unitOfMeasure());
        verify(jdbc).update("UPDATE catalog.medication SET code=?, name=?, active_ingredient=?, pharmaceutical_form=?, concentration=?, version=version+1, updated_by=?, updated_at=clock_timestamp() WHERE id=?",
            "MED-2", "Otro", "Principio", "Tableta", "10 mg", actor, row.id);
        verifyNoMoreInteractions(jdbc);
        verifyNoInteractions(intents);
        assertEquals("MED-1", row.code);
    }
}
