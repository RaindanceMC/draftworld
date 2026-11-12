package de.raindancer.modules.draftworld;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.draftworld.service.DraftWorldTrips;

/** What this module built, handed to its command. */
public record DraftWorldServices(Messages messages, DraftWorldTrips trips) {
}
