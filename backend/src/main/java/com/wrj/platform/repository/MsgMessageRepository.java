package com.wrj.platform.repository;

import com.wrj.platform.entity.MsgMessage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MsgMessageRepository extends JpaRepository<MsgMessage, Long> {

    /** 我的收件箱:全员广播 + 定向发我 */
    Page<MsgMessage> findByReceiverOrReceiverOrderByCreatedAtDesc(String receiver, String all, Pageable pageable);

    /** 我的未读数 */
    @Query("select count(m) from MsgMessage m where (m.receiver = :user or m.receiver = 'ALL') " +
            "and m.id not in (select r.messageId from MsgRead r where r.username = :user)")
    long countUnread(@Param("user") String user);
}
