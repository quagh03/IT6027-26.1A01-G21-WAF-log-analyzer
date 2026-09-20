package com.huylq.it6027.backend.entity;

import com.huylq.it6027.backend.entity.enums.WorkNoteSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "incident_work_notes")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IncidentWorkNote extends BaseEntity {

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "incident_id", nullable = false)
  private Incident incident;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "author_id")
  private User author;

  @Enumerated(EnumType.STRING)
  @Column(name = "source", nullable = false, length = 16)
  private WorkNoteSource source;

  @Column(name = "content", nullable = false, columnDefinition = "text")
  private String content;

}
