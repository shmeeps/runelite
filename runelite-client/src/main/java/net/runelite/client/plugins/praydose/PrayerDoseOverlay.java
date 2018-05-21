/*
 * Copyright (c) 2018, Ethan <https://github.com/shmeeps>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.client.plugins.praydose;

import java.awt.geom.Rectangle2D;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.time.Instant;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.Skill;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.PanelComponent;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

public class PrayerDoseOverlay extends Overlay
{
	private static final float PULSE_TIME = 1200f;

	private final Client client;
	private final PrayerDosePlugin plugin;
	private final PrayerDoseConfig config;
	private final PanelComponent panelComponent = new PanelComponent();
	private final TooltipManager tooltipManager;
	private final Color startColor = new Color(0, 255, 255);
	private final Color endColor = new Color(0, 92, 92);
	private Instant startOfLastTick = Instant.now();
	private boolean trackTick = true;

	@Inject
	private PrayerDoseOverlay(Client client, TooltipManager tooltipManager, PrayerDosePlugin plugin, PrayerDoseConfig config)
	{
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		this.client = client;
		this.tooltipManager = tooltipManager;
		this.plugin = plugin;
		this.config = config;
	}

	public void onTick()
	{
		// Only track the time on every other tick
		if (this.trackTick)
		{
			startOfLastTick = Instant.now(); //Reset the tick timer
			this.trackTick = false;
		}
		else
		{
			this.trackTick = true;
		}
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		int currentPrayer = this.client.getBoostedSkillLevel(Skill.PRAYER);
		int maxPrayer = this.client.getRealSkillLevel(Skill.PRAYER);

		int prayerPointsMissing = maxPrayer - currentPrayer;
		int prayerPotionDoses = (int)Math.floor(prayerPointsMissing / this.plugin.getPrayerPotionPointsRestored());
		int superRestoreDoses = (int)Math.floor(prayerPointsMissing / this.plugin.getSuperRestorePointsRestored());

		boolean usePrayerPotion = this.plugin.isHoldingPrayerPotion() && prayerPotionDoses > 0;
		boolean useSuperRestore = this.plugin.isHoldingSuperRestore() && superRestoreDoses > 0;

		Widget xpOrb = client.getWidget(WidgetInfo.QUICK_PRAYER_ORB);
		if (xpOrb == null)
		{
			return null;
		}

		Rectangle2D bounds = xpOrb.getBounds().getBounds2D();
		if (bounds.getX() <= 0)
		{
			return null;
		}

		this.renderTooltip(bounds);

		if (!usePrayerPotion && !useSuperRestore)
		{
			return null;
		}

		//Purposefully using height twice here as the bounds of the prayer orb includes the number sticking out the side
		int orbInnerHeight = (int) bounds.getHeight();
		int orbInnerWidth = orbInnerHeight;

		int orbInnerX = (int) (bounds.getX() + 24);//x pos of the inside of the prayer orb
		int orbInnerY = (int) (bounds.getY() - 1);//y pos of the inside of the prayer orb

		long timeSinceLastTick = Duration.between(startOfLastTick, Instant.now()).toMillis();

		float tickProgress = timeSinceLastTick / PULSE_TIME;
		tickProgress = Math.min(tickProgress, 1); // Cap between 0 and 1
		double t = tickProgress * Math.PI;	// Convert to 0 - pi

		graphics.setColor(this.colorLerp(this.startColor, this.endColor, Math.sin(t)));
		graphics.setStroke(new BasicStroke(2));
		graphics.drawOval(orbInnerX, orbInnerY, orbInnerWidth, orbInnerHeight);

		return new Dimension((int) bounds.getWidth(), (int) bounds.getHeight());
	}

	private void renderTooltip(Rectangle2D bounds)
	{
		if (!this.config.showRemainingTime() && !this.config.showPrayerBonus())
		{
			return;
		}

		Point mousePosition = client.getMouseCanvasPosition();
		panelComponent.getChildren().clear();

		if (bounds.contains(mousePosition.getX(), mousePosition.getY()))
		{
			StringBuilder b = new StringBuilder();
			b.append(String.format("Time Remaining: %s", this.plugin.getEstimatedTimeRemaining()));
			b.append("</br>");
			b.append(String.format("Prayer Bonus: %d", this.plugin.getTotalPrayerBonus()));
			this.tooltipManager.add(new Tooltip(b.toString()));
		}
	}

	private Color colorLerp(Color color1, Color color2, double t)
	{
		double r1 = color1.getRed();
		double r2 = color2.getRed();
		double g1 = color1.getGreen();
		double g2 = color2.getGreen();
		double b1 = color1.getBlue();
		double b2 = color2.getBlue();

		return new Color(
			(int)Math.round(r1 + (t * (r2 - r1))),
			(int)Math.round(g1 + (t * (g2 - g1))),
			(int)Math.round(b1 + (t * (b2 - b1)))
		);
	}
}
