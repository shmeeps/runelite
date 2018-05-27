/*
 * Copyright (c) 2017, Adam <Adam@sigterm.info>
 * Copyright (c) 2018, Psikoi <https://github.com/psikoi>
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
package net.runelite.client.plugins.hiscore;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import javax.annotation.Nullable;
import javax.imageio.ImageIO;
import javax.inject.Inject;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.Player;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.ui.components.IconTextField;
import net.runelite.client.util.RunnableExceptionLogger;
import net.runelite.client.util.StackFormatter;
import net.runelite.http.api.hiscore.HiscoreClient;
import net.runelite.http.api.hiscore.HiscoreEndpoint;
import net.runelite.http.api.hiscore.HiscoreResult;
import net.runelite.http.api.hiscore.HiscoreSkill;
import static net.runelite.http.api.hiscore.HiscoreSkill.AGILITY;
import static net.runelite.http.api.hiscore.HiscoreSkill.ATTACK;
import static net.runelite.http.api.hiscore.HiscoreSkill.BOUNTY_HUNTER_HUNTER;
import static net.runelite.http.api.hiscore.HiscoreSkill.BOUNTY_HUNTER_ROGUE;
import static net.runelite.http.api.hiscore.HiscoreSkill.CLUE_SCROLL_ALL;
import static net.runelite.http.api.hiscore.HiscoreSkill.CONSTRUCTION;
import static net.runelite.http.api.hiscore.HiscoreSkill.COOKING;
import static net.runelite.http.api.hiscore.HiscoreSkill.CRAFTING;
import static net.runelite.http.api.hiscore.HiscoreSkill.DEFENCE;
import static net.runelite.http.api.hiscore.HiscoreSkill.FARMING;
import static net.runelite.http.api.hiscore.HiscoreSkill.FIREMAKING;
import static net.runelite.http.api.hiscore.HiscoreSkill.FISHING;
import static net.runelite.http.api.hiscore.HiscoreSkill.FLETCHING;
import static net.runelite.http.api.hiscore.HiscoreSkill.HERBLORE;
import static net.runelite.http.api.hiscore.HiscoreSkill.HITPOINTS;
import static net.runelite.http.api.hiscore.HiscoreSkill.HUNTER;
import static net.runelite.http.api.hiscore.HiscoreSkill.LAST_MAN_STANDING;
import static net.runelite.http.api.hiscore.HiscoreSkill.MAGIC;
import static net.runelite.http.api.hiscore.HiscoreSkill.MINING;
import static net.runelite.http.api.hiscore.HiscoreSkill.OVERALL;
import static net.runelite.http.api.hiscore.HiscoreSkill.PRAYER;
import static net.runelite.http.api.hiscore.HiscoreSkill.RANGED;
import static net.runelite.http.api.hiscore.HiscoreSkill.RUNECRAFT;
import static net.runelite.http.api.hiscore.HiscoreSkill.SLAYER;
import static net.runelite.http.api.hiscore.HiscoreSkill.SMITHING;
import static net.runelite.http.api.hiscore.HiscoreSkill.STRENGTH;
import static net.runelite.http.api.hiscore.HiscoreSkill.THIEVING;
import static net.runelite.http.api.hiscore.HiscoreSkill.WOODCUTTING;
import net.runelite.http.api.hiscore.Skill;

@Slf4j
public class HiscorePanel extends PluginPanel
{
	/* The maximum allowed username length in runescape accounts */
	private static final int MAX_USERNAME_LENGTH = 12;

	private static final ImageIcon SEARCH_ICON;
	private static final ImageIcon LOADING_ICON;
	private static final ImageIcon ERROR_ICON;

	/**
	 * Real skills, ordered in the way they should be displayed in the panel.
	 */
	private static final List<HiscoreSkill> SKILLS = ImmutableList.of(
		ATTACK, HITPOINTS, MINING,
		STRENGTH, AGILITY, SMITHING,
		DEFENCE, HERBLORE, FISHING,
		RANGED, THIEVING, COOKING,
		PRAYER, CRAFTING, FIREMAKING,
		MAGIC, FLETCHING, WOODCUTTING,
		RUNECRAFT, SLAYER, FARMING,
		CONSTRUCTION, HUNTER
	);

	@Inject
	ScheduledExecutorService executor;

	@Inject
	@Nullable
	private Client client;

	private final HiscoreConfig config;
	private final IconTextField input;

	private final List<JLabel> skillLabels = new ArrayList<>();

	private final JPanel statsPanel = new JPanel();

	/* A list of all the selectable endpoints (ironman, deadman, etc) */
	private final List<JPanel> endPoints = new ArrayList<>();

	private final HiscoreClient hiscoreClient = new HiscoreClient();

	private HiscoreResult result;

	/* The currently selected endpoint */
	private HiscoreEndpoint selectedEndPoint;

	/* Used to prevent users from switching endpoint tabs while the results are loading */
	private boolean loading = false;

	static
	{
		try
		{
			synchronized (ImageIO.class)
			{
				SEARCH_ICON = new ImageIcon(ImageIO.read(IconTextField.class.getResourceAsStream("search.png")));
				LOADING_ICON = new ImageIcon(IconTextField.class.getResource("loading_spinner_darker.gif"));
				ERROR_ICON = new ImageIcon(ImageIO.read(IconTextField.class.getResourceAsStream("error.png")));
			}
		}
		catch (IOException e)
		{
			throw new RuntimeException(e);
		}
	}

	@Inject
	public HiscorePanel(HiscoreConfig config)
	{
		super();
		this.config = config;

		setBorder(new EmptyBorder(10, 10, 0, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		// Create GBL to arrange sub items
		GridBagLayout gridBag = new GridBagLayout();
		setLayout(gridBag);

		// Expand sub items to fit width of panel, align to top of panel
		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.anchor = GridBagConstraints.NORTH;

		input = new IconTextField();
		input.setPreferredSize(new Dimension(100, 30));
		input.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		input.setHoverBackgroundColor(ColorScheme.DARK_GRAY_HOVER_COLOR);
		input.setIcon(SEARCH_ICON);
		input.addActionListener(e -> executor.execute(RunnableExceptionLogger.wrap(this::lookup)));
		input.addMouseListener(new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if (e.getClickCount() != 2)
				{
					return;
				}
				if (client == null)
				{
					return;
				}

				Player localPlayer = client.getLocalPlayer();

				if (localPlayer != null)
				{
					executor.execute(() -> lookup(localPlayer.getName()));
				}
			}
		});

		c.gridx = 0;
		c.gridy = 0;
		c.weightx = 1;
		c.weighty = 0;
		c.insets = new Insets(0, 0, 10, 0);
		gridBag.setConstraints(input, c);
		add(input);

		/* The container for all the endpoint selectors */
		JPanel endpointPanel = new JPanel();
		endpointPanel.setLayout(new GridLayout(1, 5, 7, 1));

		for (HiscoreEndpoint endpoint : HiscoreEndpoint.values())
		{
			try
			{
				BufferedImage iconImage;
				synchronized (ImageIO.class)
				{
					iconImage = ImageIO.read(HiscorePanel.class.getResourceAsStream(
						endpoint.name().toLowerCase() + ".png"));
				}

				JPanel panel = new JPanel();
				JLabel label = new JLabel();

				label.setIcon(new ImageIcon(iconImage));

				panel.add(label);
				panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
				panel.setToolTipText(endpoint.getName() + " Hiscores");
				panel.addMouseListener(new MouseAdapter()
				{
					@Override
					public void mouseClicked(MouseEvent e)
					{
						if (loading)
						{
							return;
						}
						executor.execute(HiscorePanel.this::lookup);
						selectedEndPoint = endpoint;
						updateButtons();
					}

					@Override
					public void mouseEntered(MouseEvent e)
					{
						panel.setBackground(ColorScheme.DARKER_GRAY_HOVER_COLOR);
					}

					@Override
					public void mouseExited(MouseEvent e)
					{
						panel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
					}
				});

				endPoints.add(panel);
				endpointPanel.add(panel);
			}
			catch (IOException ex)
			{
				throw new RuntimeException(ex);
			}
		}

		/* Default endpoint is the general (normal) endpoint */
		selectedEndPoint = HiscoreEndpoint.NORMAL;
		updateButtons();

		c.gridx = 0;
		c.gridy = 1;
		gridBag.setConstraints(endpointPanel, c);
		add(endpointPanel);

		// Panel that holds skill icons
		GridLayout stats = new GridLayout(8, 3);
		statsPanel.setLayout(stats);
		statsPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		statsPanel.setBorder(new EmptyBorder(5, 0, 5, 0));

		// For each skill on the ingame skill panel, create a Label and add it to the UI
		for (HiscoreSkill skill : SKILLS)
		{
			JPanel panel = makeSkillPanel(skill);
			statsPanel.add(panel);
		}

		c.gridx = 0;
		c.gridy = 2;
		gridBag.setConstraints(statsPanel, c);
		add(statsPanel);

		JPanel totalPanel = new JPanel();
		totalPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		totalPanel.setLayout(new GridLayout(1, 2));

		totalPanel.add(makeSkillPanel(null)); //combat has no hiscore skill, refered to as null
		totalPanel.add(makeSkillPanel(OVERALL));

		c.gridx = 0;
		c.gridy = 3;
		gridBag.setConstraints(totalPanel, c);
		add(totalPanel);

		JPanel minigamePanel = new JPanel();
		// These aren't all on one row because when there's a label with four or more digits it causes the details
		// panel to change its size for some reason...
		minigamePanel.setLayout(new GridLayout(2, 3));
		minigamePanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);

		minigamePanel.add(makeSkillPanel(CLUE_SCROLL_ALL));
		minigamePanel.add(makeSkillPanel(LAST_MAN_STANDING));
		minigamePanel.add(makeSkillPanel(BOUNTY_HUNTER_ROGUE));
		minigamePanel.add(makeSkillPanel(BOUNTY_HUNTER_HUNTER));

		c.gridx = 0;
		c.gridy = 4;
		gridBag.setConstraints(minigamePanel, c);
		add(minigamePanel);
	}

	@Override
	public void onActivate()
	{
		super.onActivate();
		input.requestFocusInWindow();
	}

	/* Builds a JPanel displaying an icon and level/number associated with it */
	private JPanel makeSkillPanel(HiscoreSkill skill)
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setText("--");

		String skillIcon = "skill_icons_small/" + (skill == null ? "combat" : skill.getName().toLowerCase()) + ".png";
		log.debug("Loading skill icon from {}", skillIcon);

		try
		{
			BufferedImage icon;
			synchronized (ImageIO.class)
			{
				icon = ImageIO.read(HiscorePanel.class.getResourceAsStream(skillIcon));
			}
			label.setIcon(new ImageIcon(icon));
		}
		catch (IOException ex)
		{
			log.warn(null, ex);
		}

		boolean totalLabel = skill == HiscoreSkill.OVERALL || skill == null; //overall or combat
		label.setIconTextGap(totalLabel ? 10 : 4);

		JPanel skillPanel = new JPanel();
		skillPanel.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		skillPanel.setBorder(new EmptyBorder(2, 0, 2, 0));
		skillLabels.add(label);
		skillPanel.add(skillLabels.get(skillLabels.size() - 1));

		return skillPanel;
	}

	public void lookup(String username)
	{
		input.setText(username);

		selectedEndPoint = HiscoreEndpoint.NORMAL; //reset the endpoint to regular player
		updateButtons();

		lookup();
	}

	private void lookup()
	{
		String lookup = input.getText();

		lookup = sanitize(lookup);

		if (Strings.isNullOrEmpty(lookup))
		{
			return;
		}

		/* Runescape usernames can't be longer than 12 characters long */
		if (lookup.length() > MAX_USERNAME_LENGTH)
		{
			input.setIcon(ERROR_ICON);
			loading = false;
			return;
		}

		input.setEditable(false);
		input.setIcon(LOADING_ICON);
		loading = true;

		for (JLabel label : skillLabels)
		{
			label.setText("--");
			label.setToolTipText(null);
		}

		// if for some reason no endpoint was selected, default to normal
		if (selectedEndPoint == null)
		{
			selectedEndPoint = HiscoreEndpoint.NORMAL;
		}

		try
		{
			log.debug("Hiscore endpoint " + selectedEndPoint.name() + " selected");
			result = hiscoreClient.lookup(lookup, selectedEndPoint);
		}
		catch (IOException ex)
		{
			log.warn("Error fetching Hiscore data " + ex.getMessage());
			input.setIcon(ERROR_ICON);
			input.setEditable(true);
			loading = false;
			return;
		}

		/*
		For some reason, the fetch results would sometimes return a not null object
		with all null attributes, to check for that, i'll just null check one of the attributes.
		 */
		if (result == null || result.getAttack() == null)
		{
			input.setIcon(ERROR_ICON);
			input.setEditable(true);
			loading = false;
			return;
		}

		//successful player search
		input.setIcon(SEARCH_ICON);
		input.setEditable(true);
		loading = false;

		int index = 0;
		for (JLabel label : skillLabels)
		{
			HiscoreSkill skill = find(index);

			if (skill == null)
			{
				if (result.getPlayer() != null)
				{
					int combatLevel = Experience.getCombatLevel(
						result.getAttack().getLevel(),
						result.getStrength().getLevel(),
						result.getDefence().getLevel(),
						result.getHitpoints().getLevel(),
						result.getMagic().getLevel(),
						result.getRanged().getLevel(),
						result.getPrayer().getLevel()
					);
					label.setText(Integer.toString(combatLevel));
				}
			}
			else if (result.getSkill(skill) != null && result.getSkill(skill).getRank() != -1)
			{
				Skill s = result.getSkill(skill);
				int level;
				if (config.virtualLevels() && SKILLS.contains(skill))
				{
					level = Experience.getLevelForXp((int) s.getExperience());
				}
				else
				{
					level = s.getLevel();
				}

				label.setText(Integer.toString(level));
			}

			label.setToolTipText(detailsHtml(result, skill));
			index++;
		}
	}

	void addInputKeyListener(KeyListener l)
	{
		this.input.addKeyListener(l);
	}

	void removeInputKeyListener(KeyListener l)
	{
		this.input.removeKeyListener(l);
	}

	/*
		Returns a hiscore skill based on it's display order.
	 */
	private HiscoreSkill find(int index)
	{
		if (index < SKILLS.size())
		{
			return SKILLS.get(index);
		}

		switch (index - SKILLS.size())
		{
			case 0:
				return null;
			case 1:
				return HiscoreSkill.OVERALL;
			case 2:
				return HiscoreSkill.CLUE_SCROLL_ALL;
			case 3:
				return HiscoreSkill.LAST_MAN_STANDING;
			case 4:
				return HiscoreSkill.BOUNTY_HUNTER_ROGUE;
			case 5:
				return HiscoreSkill.BOUNTY_HUNTER_HUNTER;
		}

		return null;
	}

	/*
		Builds a html string to display on tooltip (when hovering a skill).
	 */
	private String detailsHtml(HiscoreResult result, HiscoreSkill skill)
	{
		String openingTags = "<html><body style = 'padding: 5px;color:#989898'>";
		String closingTags = "</html><body>";

		String content = "";

		if (skill == null)
		{
			double combatLevel = Experience.getCombatLevelPrecise(
				result.getAttack().getLevel(),
				result.getStrength().getLevel(),
				result.getDefence().getLevel(),
				result.getHitpoints().getLevel(),
				result.getMagic().getLevel(),
				result.getRanged().getLevel(),
				result.getPrayer().getLevel()
			);

			double combatExperience = result.getAttack().getExperience()
				+ result.getStrength().getExperience() + result.getDefence().getExperience()
				+ result.getHitpoints().getExperience() + result.getMagic().getExperience()
				+ result.getRanged().getExperience() + result.getPrayer().getExperience();

			content += "<p><span style = 'color:white'>Skill:</span> Combat</p>";
			content += "<p><span style = 'color:white'>Exact Combat Level:</span> " + StackFormatter.formatNumber(combatLevel) + "</p>";
			content += "<p><span style = 'color:white'>Experience:</span> " + StackFormatter.formatNumber(combatExperience) + "</p>";
		}
		else
		{
			switch (skill)
			{
				case CLUE_SCROLL_ALL:
				{
					String rank = (result.getClueScrollAll().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getClueScrollAll().getRank());
					String allRank = (result.getClueScrollAll().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getClueScrollAll().getRank());
					String easyRank = (result.getClueScrollEasy().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getClueScrollEasy().getRank());
					String mediumRank = (result.getClueScrollMedium().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getClueScrollMedium().getRank());
					String hardRank = (result.getClueScrollHard().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getClueScrollHard().getRank());
					String eliteRank = (result.getClueScrollElite().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getClueScrollElite().getRank());
					String masterRank = (result.getClueScrollMaster().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getClueScrollMaster().getRank());
					String all = (result.getClueScrollAll().getLevel() == -1 ? "0" : StackFormatter.formatNumber(result.getClueScrollAll().getLevel()));
					String easy = (result.getClueScrollEasy().getLevel() == -1 ? "0" : StackFormatter.formatNumber(result.getClueScrollEasy().getLevel()));
					String medium = (result.getClueScrollMedium().getLevel() == -1 ? "0" : StackFormatter.formatNumber(result.getClueScrollMedium().getLevel()));
					String hard = (result.getClueScrollHard().getLevel() == -1 ? "0" : StackFormatter.formatNumber(result.getClueScrollHard().getLevel()));
					String elite = (result.getClueScrollElite().getLevel() == -1 ? "0" : StackFormatter.formatNumber(result.getClueScrollElite().getLevel()));
					String master = (result.getClueScrollMaster().getLevel() == -1 ? "0" : StackFormatter.formatNumber(result.getClueScrollMaster().getLevel()));
					content += "<p><span style = 'color:white'>All:</span> " + all + " <span style = 'color:white'>Rank:</span> " + allRank + "</p>";
					content += "<p><span style = 'color:white'>Easy:</span> " + easy + " <span style = 'color:white'>Rank:</span> " + easyRank + "</p>";
					content += "<p><span style = 'color:white'>Medium:</span> " + medium + " <span style = 'color:white'>Rank:</span> " + mediumRank + "</p>";
					content += "<p><span style = 'color:white'>Hard:</span> " + hard + " <span style = 'color:white'>Rank:</span> " + hardRank + "</p>";
					content += "<p><span style = 'color:white'>Elite:</span> " + elite + " <span style = 'color:white'>Rank:</span> " + eliteRank + "</p>";
					content += "<p><span style = 'color:white'>Master:</span> " + master + " <span style = 'color:white'>Rank:</span> " + masterRank + "</p>";
					break;
				}
				case BOUNTY_HUNTER_ROGUE:
				{
					String rank = (result.getBountyHunterRogue().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getBountyHunterRogue().getRank());
					content += "<p><span style = 'color:white'>Rank:</span> " + rank + "</p>";
					break;
				}
				case BOUNTY_HUNTER_HUNTER:
				{
					String rank = (result.getBountyHunterHunter().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getBountyHunterHunter().getRank());
					content += "<p><span style = 'color:white'>Rank:</span> " + rank + "</p>";
					break;
				}
				case LAST_MAN_STANDING:
				{
					String rank = (result.getLastManStanding().getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(result.getLastManStanding().getRank());
					content += "<p><span style = 'color:white'>Rank:</span> " + rank + "</p>";
					break;
				}
				case OVERALL:
				{
					Skill requestedSkill = result.getSkill(skill);
					String rank = (requestedSkill.getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(requestedSkill.getRank());
					String exp = (requestedSkill.getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(requestedSkill.getExperience());
					content += "<p><span style = 'color:white'>Skill:</span> " + skill.getName() + "</p>";
					content += "<p><span style = 'color:white'>Rank:</span> " + rank + "</p>";
					content += "<p><span style = 'color:white'>Experience:</span> " + exp + "</p>";
					break;
				}
				default:
				{
					Skill requestedSkill = result.getSkill(skill);

					String rank = (requestedSkill.getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(requestedSkill.getRank());
					String exp = (requestedSkill.getRank() == -1) ? "Unranked" : StackFormatter.formatNumber(requestedSkill.getExperience());
					String remainingXp;
					if (requestedSkill.getRank() == -1)
					{
						remainingXp = "Unranked";
					}
					else
					{
						int currentLevel = Experience.getLevelForXp((int) requestedSkill.getExperience());
						remainingXp = (currentLevel + 1 <= Experience.MAX_VIRT_LEVEL) ? StackFormatter.formatNumber(Experience.getXpForLevel(currentLevel + 1) - requestedSkill.getExperience()) : "0";
					}

					content += "<p><span style = 'color:white'>Skill:</span> " + skill.getName() + "</p>";
					content += "<p><span style = 'color:white'>Rank:</span> " + rank + "</p>";
					content += "<p><span style = 'color:white'>Experience:</span> " + exp + "</p>";
					content += "<p><span style = 'color:white'>Remaining XP:</span> " + remainingXp + "</p>";

					break;
				}
			}
		}

		/**
		 * Adds a html progress bar to the hover information
		 */
		if (SKILLS.contains(skill))
		{
			long experience = result.getSkill(skill).getExperience();
			if (experience >= 0)
			{
				int currentXp = (int) experience;
				int currentLevel = Experience.getLevelForXp(currentXp);
				int xpForCurrentLevel = Experience.getXpForLevel(currentLevel);
				int xpForNextLevel = currentLevel + 1 <= Experience.MAX_VIRT_LEVEL ? Experience.getXpForLevel(currentLevel + 1) : -1;

				double xpGained = currentXp - xpForCurrentLevel;
				double xpGoal = xpForNextLevel != -1 ? xpForNextLevel - xpForCurrentLevel : 100;
				int progress = (int) ((xpGained / xpGoal) * 100f);

				// had to wrap the bar with an empty div, if i added the margin directly to the bar, it would mess up
				content += "<div style = 'margin-top:3px'>"
					+ "<div style = 'background: #070707; border: 1px solid #070707; height: 6px; width: 100%;'>"
					+ "<div style = 'height: 6px; width: " + progress + "%; background: #dc8a00;'>"
					+ "</div>"
					+ "</div>"
					+ "</div>";
			}
		}

		return openingTags + content + closingTags;
	}

	private static String sanitize(String lookup)
	{
		return lookup.replace('\u00A0', ' ');
	}

	/*
		When an endpoint gets selected, this method will correctly display the selected one
		with an orange underline.
	 */
	private void updateButtons()
	{
		for (JPanel panel : endPoints)
		{
			panel.setBorder(new EmptyBorder(0, 0, 1, 0));
		}

		int selectedIndex = selectedEndPoint.ordinal();
		endPoints.get(selectedIndex).setBorder(new MatteBorder(0, 0, 1, 0, ColorScheme.BRAND_ORANGE));
	}
}