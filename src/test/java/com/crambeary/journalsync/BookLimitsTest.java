package com.crambeary.journalsync;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.component.WritableBookContent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BookLimitsTest {
	@BeforeAll
	static void bootstrapMinecraft() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
	}

	@Test
	void writableBookHasOneHundredPages() {
		Assertions.assertEquals(100, WritableBookContent.MAX_PAGES);
	}

	@Test
	void writableBookPageHoldsOneThousandTwentyFourCharacters() {
		Assertions.assertEquals(1024, WritableBookContent.PAGE_EDIT_LENGTH);
	}

	@Test
	void bookContentRefusesMoreThanTheMaximumPages() {
		var pages = java.util.Collections.nCopies(
			WritableBookContent.MAX_PAGES + 1, net.minecraft.server.network.Filterable.passThrough("x"));
		Assertions.assertThrows(IllegalArgumentException.class, () -> new WritableBookContent(pages));
	}
}
