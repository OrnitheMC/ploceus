package net.ornithemc.ploceus;

import java.util.function.Consumer;
import java.util.function.Predicate;

import net.fabricmc.loom.configuration.providers.minecraft.library.Library;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryContext;
import net.fabricmc.loom.configuration.providers.minecraft.library.LibraryProcessor;
import net.fabricmc.loom.util.Platform;

public class LibraryUpgrader extends LibraryProcessor {

	private final PloceusGradleExtension ploceus;

	public LibraryUpgrader(PloceusGradleExtension ploceus, Platform platform, LibraryContext context) {
		super(platform, context);

		this.ploceus = ploceus;
	}

	@Override
	public ApplicationResult getApplicationResult() {
		return ploceus.shouldUpgradeLibraries() ? ApplicationResult.MUST_APPLY : ApplicationResult.DONT_APPLY;
	}

	@Override
	public Predicate<Library> apply(Consumer<Library> dependencyConsumer) {
		for (Library upgrade : ploceus.getLibraryUpgrades()) {
			dependencyConsumer.accept(upgrade);
		}

		return ALLOW_ALL;
	}
}
