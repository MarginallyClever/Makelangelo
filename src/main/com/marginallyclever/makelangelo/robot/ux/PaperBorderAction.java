package com.marginallyclever.makelangelo.robot.ux;

import java.awt.event.ActionEvent;

import javax.swing.AbstractAction;

import com.marginallyclever.makelangelo.robot.Paper;
import com.marginallyclever.makelangelo.robot.RobotController;

public class PaperBorderAction extends AbstractAction {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private RobotController myController;
	
	public PaperBorderAction(RobotController robotController,String name) {
		super(name);
		this.myController=robotController;
	}
	
	@Override
	public void actionPerformed(ActionEvent arg0) {
		Paper paper = myController.getPaper();
		myController.movePenAbsolute(paper.getLeft(),paper.getTop());
		myController.lowerPen();
		myController.movePenAbsolute(paper.getRight(),paper.getTop());
		myController.movePenAbsolute(paper.getRight(),paper.getBottom());
		myController.movePenAbsolute(paper.getLeft(),paper.getBottom());
		myController.movePenAbsolute(paper.getLeft(),paper.getTop());
		myController.raisePen();
		myController.goHome();
	}
}
