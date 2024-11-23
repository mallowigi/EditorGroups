package krasa.editorGroups.support;

import com.intellij.openapi.util.SystemInfo;
import com.intellij.ui.ColorChooserService;
import com.intellij.ui.JBColor;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * @author Konstantin Bulenkov
 */
public class CheckBoxWithColorChooser extends JPanel {
  protected MyColorButton myColorButton;
  private JCheckBox myCheckbox;
  private Color myColor;
  private Dimension colorDimension;

  public CheckBoxWithColorChooser(String text, Boolean selected, Color defaultColor) {
    this(text, selected, JBColor.WHITE, defaultColor);
  }

  public CheckBoxWithColorChooser(String text, Color defaultColor) {
    this(text, false, defaultColor);
  }

  public CheckBoxWithColorChooser(String text, Boolean selected, Color color, Color defaultColor) {
    setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
    myColor = color;
    if (selected != null) {
      myCheckbox = new JCheckBox(text, selected);
      add(myCheckbox);
    }
    myColorButton = new MyColorButton();
    add(myColorButton);
    addMouseListener(new MouseAdapter() {
      @Override
      public void mousePressed(MouseEvent e) {
        myColorButton.mouseAdapter.mousePressed(e);
      }
    });

    if (defaultColor != null) {
      JPanel comp = new JPanel();
      comp.setSize(20, 0);
      add(comp);
      JButton defaultButton = new JButton("Reset to default");
      add(defaultButton);
      defaultButton.addActionListener(e -> {
        myColor = defaultColor;
        this.repaint();
      });
    }
    colorDimension = new Dimension(18, 18);
  }

  public CheckBoxWithColorChooser setColorDimension(Dimension colorDimension) {
    this.colorDimension = colorDimension;
    return this;
  }

  public void setMnemonic(char c) {
    myCheckbox.setMnemonic(c);
  }

  public Color getColor() {
    return myColor;
  }

  public void setColor(Integer color) {
    if (color != null) {
      myColor = new JBColor(new Color(color), new Color(color));
    }
  }

  public void setColor(Color color) {
    myColor = color;
  }

  public int getColorAsRGB() {
    return myColor.getRGB();
  }

  public boolean isSelected() {
    return myCheckbox.isSelected();
  }

  public void setSelected(boolean selected) {
    myCheckbox.setSelected(selected);
  }

  public void onColorChanged() {
    repaint();
  }

  private class MyColorButton extends JButton {
    protected MouseAdapter mouseAdapter;

    MyColorButton() {
      setMargin(JBUI.emptyInsets());
      setFocusable(false);
      setDefaultCapable(false);
      setFocusable(false);
      if (SystemInfo.isMac) {
        putClientProperty("JButton.buttonType", "square");
      }

      mouseAdapter = new MouseAdapter() {
        @Override
        public void mousePressed(MouseEvent e) {
          Color color = ColorChooserService.getInstance().showDialog(MyColorButton.this, "Choose color",
            CheckBoxWithColorChooser.this.myColor);
          if (color != null) {
            if (myCheckbox != null && !myCheckbox.isSelected()) {
              myCheckbox.setSelected(true);
            }
            myColor = color;
            onColorChanged();
          }
        }
      };
      addMouseListener(mouseAdapter);
    }

    @Override
    public void paint(Graphics g) {
      Color color = g.getColor();

      g.setColor(myColor);
      g.fillRect(0, 0, getWidth(), getHeight());
      g.setColor(color);

      g.setColor(JBColor.BLACK);
      g.drawRect(0, 0, getWidth() - 1, getHeight() - 1);

    }

    @Override
    public Dimension getMinimumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getMaximumSize() {
      return getPreferredSize();
    }

    @Override
    public Dimension getPreferredSize() {
      return colorDimension;
    }
  }
}
