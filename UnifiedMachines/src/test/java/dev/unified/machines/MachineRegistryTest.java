package dev.unified.machines;

import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

final class MachineRegistryTest {
@TempDir Path temp;

@Test void loaderLimitIsPersistedAcrossRestart() throws Exception {
MachineRegistry reg=new MachineRegistry(temp.resolve("machines.properties"));
UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
MachineRegistry.Position first=new MachineRegistry.Position(world,16,64,16);
MachineRegistry.Position second=new MachineRegistry.Position(world,48,64,48);
assertTrue(reg.add(new MachineRegistry.Machine(first,owner,MachineRegistry.Kind.LOADER)));
assertFalse(reg.add(new MachineRegistry.Machine(second,owner,MachineRegistry.Kind.LOADER)));
reg.save();
MachineRegistry restored=new MachineRegistry(temp.resolve("machines.properties"));
restored.load();
assertEquals(1,restored.count(MachineRegistry.Kind.LOADER));
assertFalse(restored.add(new MachineRegistry.Machine(second,owner,MachineRegistry.Kind.LOADER)));
}
@Test void ownerCanMoveLoaderAfterRemoval() throws Exception {
MachineRegistry reg=new MachineRegistry(temp.resolve("machines.properties"));
UUID owner=UUID.randomUUID(),world=UUID.randomUUID();
MachineRegistry.Machine old=new MachineRegistry.Machine(new MachineRegistry.Position(world,0,70,0),owner,MachineRegistry.Kind.LOADER);
MachineRegistry.Machine moved=new MachineRegistry.Machine(new MachineRegistry.Position(world,32,70,32),owner,MachineRegistry.Kind.LOADER);
assertTrue(reg.add(old));
reg.remove(old);
assertTrue(reg.add(moved));
assertEquals(1,reg.count(MachineRegistry.Kind.LOADER));
}
@Test void independentOwnersCanShareAChunk() throws Exception {
MachineRegistry reg=new MachineRegistry(temp.resolve("machines.properties"));
UUID world=UUID.randomUUID();
assertTrue(reg.add(new MachineRegistry.Machine(new MachineRegistry.Position(world,0,70,0),UUID.randomUUID(),MachineRegistry.Kind.LOADER)));
assertTrue(reg.add(new MachineRegistry.Machine(new MachineRegistry.Position(world,1,70,0),UUID.randomUUID(),MachineRegistry.Kind.LOADER)));
assertEquals(2,reg.count(MachineRegistry.Kind.LOADER));
}
}
