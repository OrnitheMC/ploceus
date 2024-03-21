package net.ornithemc.ploceus.mappings;

import org.gradle.api.provider.Property;

import net.fabricmc.loom.configuration.providers.mappings.IntermediaryMappingsProvider;

import net.ornithemc.ploceus.api.GameSide;

public abstract class CalamusGen1Provider extends IntermediaryMappingsProvider {

	public abstract Property<GameSide> getSide();

	@Override
	public String getName() {
		return getSide().get().prefix() + "calamus-" + super.getName();
	}
}
