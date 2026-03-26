package org.quetoo.installer;

import javax.swing.*;
import java.io.Serial;

/**
 * The top level container for the user interface.
 *
 * @author jdolan
 */
public class Frame extends JFrame {

  @Serial
  private static final long serialVersionUID = 1L;

  /**
   * Instantiates a {@link Frame} with the specified {@link Manager}
   *
   * @param manager The Manager.
   */
  public Frame(final Manager manager) {

    super(Config.NAME + " " + Config.VERSION);

    setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

    final var panel = new Panel(manager);

    setContentPane(panel);

    pack();
    setSize(960, getHeight());
    setLocationRelativeTo(null);
    setVisible(true);
  }
}
