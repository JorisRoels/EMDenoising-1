package be.vib.imagej;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;

/**
 * 
 * A simple wizard consisting of a number of pages that can be traversed forward and backward in a linear fashion.
 * 
 * The wizard has no cancel button - user needs to close the dialog via the window's close button to cancel
 * (probably we better pop up a confirmation dialog first).
 *
 */
public class Wizard extends JDialog
                    implements ActionListener 
{		
	private CardLayout cardLayout;
	
	private JPanel pagesPanel;  // holds all WizardPages in the Wizard
	private int currentPageIdx = 0;
		
	private JLabel crumbs;  // "bread crumbs" at the top, showing where the user is in the linear wizard
	
	private JButton backButton;
	private JButton nextButton;
	// No cancel or finish buttons
	
	public Wizard(String title)
	{						
		buildUI(title);
		
	}
	
	public void addPage(WizardPage page)
	{
		pagesPanel.add(page);

		updateButtons();
		updateCrumbs();
	}
	
	// Call start() after all pages have been added to the wizard via addPage()
	// and before showing the wizard via setVisible(true).
	public void start()
	{
		currentPageIdx = 0;
		WizardPage firstPage = (WizardPage)pagesPanel.getComponent(currentPageIdx);
		firstPage.aboutToShowPanel();
	}
	
	private void buildUI(String title)
	{		
		JPanel buttonsPanel = new JPanel();
		
		pagesPanel = new JPanel();
		
		cardLayout = new CardLayout();
		pagesPanel.setLayout(cardLayout);
		
		backButton = new JButton("Back");
		nextButton = new JButton("Next");
		
		backButton.addActionListener(this);
		nextButton.addActionListener(this);
		
		buttonsPanel.setLayout(new BorderLayout());
		
		Box buttonsBox = new Box(BoxLayout.X_AXIS);
		buttonsBox.setBorder(new EmptyBorder(new Insets(10, 10, 10, 10)));
		buttonsBox.add(backButton);
		buttonsBox.add(nextButton);
		buttonsBox.add(Box.createHorizontalStrut(10));
		
		buttonsPanel.add(buttonsBox, BorderLayout.EAST);
		
		crumbs = new JLabel();
		crumbs.setBorder(new EmptyBorder(new Insets(10, 10, 10, 10)));
		crumbs.setHorizontalAlignment(JLabel.CENTER);
		
		setMinimumSize(new Dimension(160, 80));
		setTitle(title);

		getContentPane().add(crumbs, BorderLayout.NORTH);
		getContentPane().add(pagesPanel, BorderLayout.CENTER);
		getContentPane().add(buttonsPanel, BorderLayout.SOUTH);
	}

	@Override
	public void actionPerformed(ActionEvent event)
	{
		Object source = event.getSource();
		if (source != backButton && source != nextButton)
			return;
						
		if (source == backButton)
		{
			int newPageIdx = currentPageIdx - 1;

			WizardPage newPage = (WizardPage)pagesPanel.getComponent(newPageIdx);
			newPage.aboutToShowPanel();
			
			cardLayout.previous(pagesPanel);
			currentPageIdx = newPageIdx;
		}
		else if (source == nextButton)
		{
			int newPageIdx = currentPageIdx + 1;

			WizardPage newPage = (WizardPage)pagesPanel.getComponent(newPageIdx);
			newPage.aboutToShowPanel();

			cardLayout.next(pagesPanel);
			currentPageIdx = newPageIdx;
		}

		updateButtons();
		updateCrumbs();
	}
	
	/**
	 *  Enables or disables the buttons based on which panel is currently active
	 */
	public void updateButtons()
	{
		final int numPages = pagesPanel.getComponentCount();
		if (numPages == 0)
			return;
		
		final WizardPage currentPage = (WizardPage)pagesPanel.getComponent(currentPageIdx);
		
		backButton.setEnabled((currentPageIdx > 0) && currentPage.canGoToPreviousPage());
		nextButton.setEnabled((currentPageIdx < numPages - 1) && currentPage.canGoToNextPage());		
	}
	
	/*
	 * Position the wizard horizontally in the center of the screen,
	 * and vertically somewhat above center.
	 */
	public void moveToMiddleOfScreen()
	{
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int wizardWidth = getSize().width;
        int wizardHeight = getSize().height;
        int x = (screen.width - wizardWidth) / 2; 
        int y = (screen.height - wizardHeight) / 3;
        setLocation(x, y);
	}
	
	private void updateCrumbs()
	{
		crumbs.setText(crumbsHTML(currentPageIdx));
	}

	/**
	 * Builds an HTML string that represents the trail of bread crumbs when traveling
	 * through the wizard pages from beginning to end. The crumb for one wizard
	 * page is highlighted (suggesting it is the current page), the crumbs for all
	 * other pages are dimmed.
	 * 
	 * @param idxToHighlight The index of the wizard page that needs to be highlighted
	 *        in the bread crumbs trail.
	 * @return An HTML string representing the bread crumbs trail. It uses HTML color 
	 *         tags for dimming/highlighting the crumbs.
	 */
	private String crumbsHTML(int idxToHighlight)
	{
		String crumbs = "<html>";
		
		final int numPages = pagesPanel.getComponentCount();
		
		for (int i = 0; i <numPages; i++)
		{
			WizardPage page = (WizardPage)pagesPanel.getComponent(i);
			String color = (i == idxToHighlight) ? "black" : "gray";
			
			crumbs = crumbs + "<font color=" + color + " > " + page.getName() + "</font>";
			if (i < numPages - 1)
				crumbs = crumbs + "<font color=gray> > </font>";
		}
		
		crumbs = crumbs + "</html>";
		
		return crumbs;
	}
}
