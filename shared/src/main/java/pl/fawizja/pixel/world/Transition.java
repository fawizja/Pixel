package pl.fawizja.pixel.world;

/** Przejscie z kafelka zrodlowego na kafelek docelowy (zwykle na innym pietrze). */
public record Transition(Position from, TransitionType type, Position to) {}
