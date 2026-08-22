package com.boris.memory;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.time.Instant;
import java.util.List;

@Repository
public interface ConversationRepository extends JpaRepository<ConversationMessage, Long> {

    List<ConversationMessage> findBySessionIdOrderByTimestampAsc(String sessionId);

    List<ConversationMessage> findBySessionIdOrderByTimestampDesc(String sessionId);

    @Query("SELECT m FROM ConversationMessage m WHERE m.sessionId = :sessionId AND m.timestamp >= :since ORDER BY m.timestamp ASC")
    List<ConversationMessage> findBySessionIdAndTimestampAfter(@Param("sessionId") String sessionId, @Param("since") Instant since);

    @Query("SELECT m FROM ConversationMessage m WHERE m.sessionId = :sessionId ORDER BY m.timestamp DESC")
    Page<ConversationMessage> findBySessionIdOrderByTimestampDesc(@Param("sessionId") String sessionId, Pageable pageable);

    long countBySessionId(String sessionId);

    void deleteBySessionId(String sessionId);

    @Query("SELECT m FROM ConversationMessage m WHERE m.sessionId = :sessionId AND (LOWER(m.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) ORDER BY m.timestamp DESC")
    List<ConversationMessage> findByKeyword(@Param("sessionId") String sessionId, @Param("keyword") String keyword, Pageable pageable);
}
