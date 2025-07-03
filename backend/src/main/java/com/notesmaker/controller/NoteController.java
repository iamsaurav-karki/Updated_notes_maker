package com.notesmaker.controller;

import com.notesmaker.model.Note;
import com.notesmaker.service.NoteService;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.annotation.*;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Controller("/api/notes")
public class NoteController {
    @Inject
    private NoteService noteService;

    @Get
    public List<Note> getAllNotes() {
        return noteService.getAllNotes();
    }

    @Get("/{id}")
    public HttpResponse<Note> getNoteById(UUID id) {
        Optional<Note> note = noteService.getNoteById(id);
        return note.map(HttpResponse::ok).orElse(HttpResponse.notFound());
    }

    @Post
    public HttpResponse<Note> createNote(@Body Note note) {
        Note created = noteService.createNote(note);
        return HttpResponse.created(created);
    }

    @Delete("/{id}")
    public HttpResponse<?> deleteNoteById(UUID id) {
        noteService.deleteNoteById(id);
        return HttpResponse.noContent();
    }

    @Put("/{id}")
    public HttpResponse<Note> updateNote(UUID id, @Body Note note) {
        note.setId(id);
        Note updated = noteService.updateNote(note);
        return HttpResponse.ok(updated);
    }

    @Options
    public HttpResponse<?> options() {
        return HttpResponse.ok();
    }

    @Options("/{id}")
    public HttpResponse<?> optionsForId(String id) {
        return HttpResponse.ok();
    }
} 
