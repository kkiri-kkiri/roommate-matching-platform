package com.kkirikkiri.matching.service;

/** Published within the input write transaction, consumed only after successful commit. */
public record MatchingInputsChanged(Long userId, boolean incoming, boolean outgoing) {}
