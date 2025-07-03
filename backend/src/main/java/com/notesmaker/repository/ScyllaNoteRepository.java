package com.notesmaker.repository;
import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.*;
import com.notesmaker.model.Note;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.InetSocketAddress;
import java.util.Arrays;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.Instant;

@Singleton
public class ScyllaNoteRepository implements NoteRepository {
    private static final Logger logger = LoggerFactory.getLogger(ScyllaNoteRepository.class);
    
    private final CqlSession session;
    private final String keyspace;
    
    private static String CREATE_TABLE_QUERY = """
        CREATE TABLE IF NOT EXISTS notes_maker.notes (
            id uuid PRIMARY KEY,
            title text,
            content text,
            created_at timestamp,
            updated_at timestamp
        )
    """;
    
    private static String INSERT_QUERY = 
        "INSERT INTO notes_maker.notes (id, title, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?)";
    
    private static String SELECT_ALL_QUERY = "SELECT * FROM notes_maker.notes";
    
    private static String SELECT_BY_ID_QUERY = "SELECT * FROM notes_maker.notes WHERE id = ?";
    
    private static String UPDATE_QUERY = 
        "UPDATE notes_maker.notes SET title = ?, content = ?, updated_at = ? WHERE id = ?";
    
    private static String DELETE_QUERY = "DELETE FROM notes_maker.notes WHERE id = ?";

//    public ScyllaNoteRepository(CqlSession session, @Value("${cassandra.default.keyspace}") String keyspace) {
//        this.session = session;
//        this.keyspace = keyspace;
//        initializeTable();
//    }
	public ScyllaNoteRepository(
	        @Value("${cassandra.default.contact-points}") String contactPoints,
	        @Value("${cassandra.default.local-datacenter}") String localDatacenter,
	        @Value("${cassandra.default.keyspace}") String keyspace) {
	    
	    this.keyspace = keyspace;
	    
	    // Parse contact points (comma-separated)
	    List<InetSocketAddress> contactPointsList = Arrays.stream(contactPoints.split(","))
	            .map(String::trim)
	            .map(cp -> {
	                String[] parts = cp.split(":");
	                String host = parts[0];
	                int port = parts.length > 1 ? Integer.parseInt(parts[1]) : 9042;
	                return new InetSocketAddress(host, port);
	            })
	            .collect(Collectors.toList());
	    
	    // Create CqlSession with environment variables
	    this.session = CqlSession.builder()
	            .addContactPoints(contactPointsList)
	            .withLocalDatacenter(localDatacenter)
	            .withKeyspace(keyspace)
	            .build();
	    
	    // Initialize queries with dynamic keyspace
	    this.CREATE_TABLE_QUERY = String.format("""
	        CREATE TABLE IF NOT EXISTS %s.notes (
	            id uuid PRIMARY KEY,
	            title text,
	            content text,
	            created_at timestamp,
	            updated_at timestamp
	        )
	        """, keyspace);
	    
	    this.INSERT_QUERY = String.format("INSERT INTO %s.notes (id, title, content, created_at, updated_at) VALUES (?, ?, ?, ?, ?)", keyspace);
	    this.SELECT_ALL_QUERY = String.format("SELECT * FROM %s.notes", keyspace);
	    this.SELECT_BY_ID_QUERY = String.format("SELECT * FROM %s.notes WHERE id = ?", keyspace);
	    this.UPDATE_QUERY = String.format("UPDATE %s.notes SET title = ?, content = ?, updated_at = ? WHERE id = ?", keyspace);
	    this.DELETE_QUERY = String.format("DELETE FROM %s.notes WHERE id = ?", keyspace);
	    
	    System.out.println("Initialized ScyllaDB connection with contact points: " + contactPoints + ", keyspace: " + keyspace);
	    
	    initializeTable();
	}

    private void initializeTable() {
        try {
            session.execute(CREATE_TABLE_QUERY);
            logger.info("Notes table initialized successfully");
        } catch (Exception e) {
            logger.error("Error initializing notes table", e);
            throw new RuntimeException("Failed to initialize database table", e);
        }
    }

    @Override
    public List<Note> findAll() {
        try {
            ResultSet resultSet = session.execute(SELECT_ALL_QUERY);
            return resultSet.all().stream()
                    .map(this::mapRowToNote)
                    .collect(Collectors.toList());
        } catch (Exception e) {
            logger.error("Error fetching all notes", e);
            throw new RuntimeException("Failed to fetch notes", e);
        }
    }

    @Override
    public Optional<Note> findById(UUID id) {
        try {
            PreparedStatement preparedStatement = session.prepare(SELECT_BY_ID_QUERY);
            BoundStatement boundStatement = preparedStatement.bind(id);
            ResultSet resultSet = session.execute(boundStatement);
            
            Row row = resultSet.one();
            return row != null ? Optional.of(mapRowToNote(row)) : Optional.empty();
        } catch (Exception e) {
            logger.error("Error fetching note by id: {}", id, e);
            throw new RuntimeException("Failed to fetch note", e);
        }
    }

    @Override
    public Note save(Note note) {
        if (note.getId() == null) {
            note.setId(UUID.randomUUID());
        }
        // Set timestamps if null
        Instant now = Instant.now();
        if (note.getCreatedAt() == null) {
            note.setCreatedAt(now);
        }
        if (note.getUpdatedAt() == null) {
            note.setUpdatedAt(now);
        }
        try {
            PreparedStatement preparedStatement = session.prepare(INSERT_QUERY);
            BoundStatement boundStatement = preparedStatement.bind(
                    note.getId(),
                    note.getTitle(),
                    note.getContent(),
                    note.getCreatedAt(),
                    note.getUpdatedAt()
            );
            session.execute(boundStatement);
            return note;
        } catch (Exception e) {
            logger.error("Error saving note", e);
            throw new RuntimeException("Failed to save note", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        try {
            PreparedStatement preparedStatement = session.prepare(DELETE_QUERY);
            BoundStatement boundStatement = preparedStatement.bind(id);
            session.execute(boundStatement);
        } catch (Exception e) {
            logger.error("Error deleting note with id: {}", id, e);
            throw new RuntimeException("Failed to delete note", e);
        }
    }

    @Override
    public Note update(Note note) {
        try {
            note.setUpdatedAt(Instant.now());
            PreparedStatement preparedStatement = session.prepare(UPDATE_QUERY);
            BoundStatement boundStatement = preparedStatement.bind(
                    note.getTitle(),
                    note.getContent(),
                    note.getUpdatedAt(),
                    note.getId()
            );
            session.execute(boundStatement);
            return note;
        } catch (Exception e) {
            logger.error("Error updating note", e);
            throw new RuntimeException("Failed to update note", e);
        }
    }

    private Note mapRowToNote(Row row) {
        UUID id = row.getUuid("id");
        String title = row.getString("title");
        String content = row.getString("content");
        Instant createdAt = row.getInstant("created_at");
        Instant updatedAt = row.getInstant("updated_at");
        return new Note(id, title, content, createdAt, updatedAt);
    }
} 
