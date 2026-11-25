package pvp;
public final class GuiState {
private boolean open, locked, previousDown;
public void update(boolean down, boolean canOpen) {
if (down && !previousDown && !locked && (open || canOpen)) open = !open;
previousDown = down;
}
public void lock() { open=false; locked=true; }
public void close() { open=false; }
public boolean isOpen() { return open; }
public boolean isLocked() { return locked; }
}