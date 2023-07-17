package net.ornithemc.ploceus;

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
}
