package land.beezz.keys.input;

/** Before 1.21.9 a click reached a screen as x, y and the button: the same methods as the game's later MouseButtonEvent. */
public record MouseButtonEvent(double x, double y, int button) {
}
