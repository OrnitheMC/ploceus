package net.ornithemc.ploceus;

import java.util.NoSuchElementException;

public enum GameSide {

	CLIENT("client"), SERVER("server"), MERGED("merged");

	private GameSide(String id) {
		this.id = id;
	}

	private final String id;

	public String id() {
		return id;
	}

	public String prefix() {
		return this == MERGED ? "" : id + "-";
	}

	public String suffix() {
		return this == MERGED ? "" : "-" + id;
	}

	public static GameSide of(String side) {
		if ("*".equals(side)) {
			return MERGED;
		}
		if ("client".equals(side)) {
			return CLIENT;
		}
		if ("server".equals(side)) {
			return SERVER;
		}

		throw new NoSuchElementException("no game side with name " + side);
	}
}
