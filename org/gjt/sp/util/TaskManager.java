/*
 * jEdit - Programmer's Text Editor
 * :tabSize=4:indentSize=4:noTabs=false:
 * :folding=explicit:collapseFolds=1:
 *
 * Copyright (C) 2010 jEdit contributors
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */
package org.gjt.sp.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The TaskManager manage Tasks in the Threadpool, it knows all of them, and
 * sends events to TaskListeners.
 *
 * @author Matthieu Casanova
 */
public enum TaskManager
{
	INSTANCE;

	/** for jEdit 5.0 API compatibility only. 
		@deprecated
	*/
	@Deprecated
	public static final TaskManager instance = INSTANCE;


	private final List<TaskListener> listeners;

	private final List<Task> tasks;
	private final List<Task> ioTasks;
	private final Object ioWaitLock;

	private TaskManager()
	{
		listeners = new CopyOnWriteArrayList<TaskListener>();
		tasks = Collections.synchronizedList(new ArrayList<Task>());
		ioTasks = Collections.synchronizedList(new ArrayList<Task>());
		ioWaitLock = new Object();
	}

	/**
	 * Return the number of tasks in queue.
	 *
	 * @return the number of tasks in queue
	 * @since jEdit 4.5pre1
	 */
	public int countTasks()
	{
		return tasks.size();
	}

	/**
	 * Return the number of IO tasks in queue.
	 *
	 * @return the number of IO tasks in queue
	 * @since jEdit 5.1pre1
	 */
	public int countIoTasks()
	{
		return ioTasks.size();
	}

	public void addTaskListener(TaskListener listener)
	{
		if (!listeners.contains(listener))
		{
			listeners.add(listener);
		}
	}

	public void removeTaskListener(TaskListener listener)
	{
		if (listeners.contains(listener))
		{
			listeners.remove(listener);
		}
	}

	void fireWaiting(Task task)
	{
		if(task.getIoTask())
			ioTasks.add(task);
		else
			tasks.add(task);

		List<TaskListener> listeners = this.listeners;
		for (TaskListener listener : listeners)
		{
			listener.waiting(task);
		}
	}

	void fireRunning(Task task)
	{
		List<TaskListener> listeners = this.listeners;
		for (TaskListener listener : listeners)
		{
			listener.running(task);
		}
	}

	void fireDone(Task task)
	{
		if(task.getIoTask())
			ioTasks.remove(task);
		else
			tasks.remove(task);

		List<TaskListener> listeners = this.listeners;
		for (TaskListener listener : listeners)
		{
			listener.done(task);
		}

		if(task.getIoTask())
		{
			AwtRunnableQueue.INSTANCE.queueAWTRunner(false);

			synchronized (ioWaitLock)
			{
				ioWaitLock.notifyAll();
			}
		}
	}

	void fireStatusUpdated(Task task)
	{
		List<TaskListener> listeners = this.listeners;
		for (TaskListener listener : listeners)
		{
			listener.statusUpdated(task);
		}
	}

	void fireValueUpdated(Task task)
	{
		List<TaskListener> listeners = this.listeners;
		for (TaskListener listener : listeners)
		{
			listener.valueUpdated(task);
		}
	}

	void fireMaximumUpdated(Task task)
	{
		List<TaskListener> listeners = this.listeners;
		for (TaskListener listener : listeners)
		{
			listener.maximumUpdated(task);
		}
	}

	/**
	 * Visit all tasks.
	 * While doing this the task list is locked
	 *
	 * @param visitor the visitor
	 */
	public void visit(TaskVisitor visitor)
	{
		synchronized (tasks)
		{
			for (Task task : tasks)
			{
				visitor.visit(task);
			}
		}
	}

	public void waitForIoTasks()
	{
		synchronized (ioWaitLock)
		{
			while(countIoTasks() > 0)
			{
				try
				{
					ioWaitLock.wait();
				} catch (InterruptedException e)
				{
					Log.log(Log.ERROR,this,e);
				}
			}
		}

		AwtRunnableQueue.INSTANCE.queueAWTRunner(true);
	}

	/**
	 * Encapsulate a runnable into a task.
	 * It is done by the Threadpool when receiving a simple Runnable
	 *
	 * @param runnable the runnable to encapsulate
	 * @return a Task
	 */
	static Task decorate(Runnable runnable)
	{
		return new MyTask(runnable);
	}

	public interface TaskVisitor
	{
		void visit(Task task);
	}

	private static class MyTask extends Task
	{
		private final Runnable runnable;

		private MyTask(Runnable runnable)
		{
			this.runnable = runnable;
		}

		public String getStatus() {
			return runnable.toString();
		}

		public String toString() {
			return runnable.toString();	
		}
		
		@Override
		public void _run()
		{
			runnable.run();
		}
	}
}
