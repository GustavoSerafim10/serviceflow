package com.gustavoserafim.serviceflow.entity;

/** Tipos de evento registrados no histórico de um chamado. */
public enum HistoryAction {
    CREATED,
    ASSIGNED,
    STATUS_CHANGED,
    COMMENT_ADDED
}
