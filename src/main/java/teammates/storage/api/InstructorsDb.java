package teammates.storage.api;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.logging.Logger;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;

import teammates.common.Assumption;
import teammates.common.Common;
import teammates.common.datatransfer.InstructorAttributes;
import teammates.common.exception.EntityAlreadyExistsException;
import teammates.storage.datastore.Datastore;
import teammates.storage.entity.Instructor;

/**
 * Handles CRUD Operations for instructor roles.
 * The API uses data transfer classes (i.e. *Attributes) instead of presistable classes.
 * 
 */
public class InstructorsDb {
	public static final String ERROR_UPDATE_NON_EXISTENT_ACCOUNT = "Trying to update non-existent Account: ";
	public static final String ERROR_UPDATE_NON_EXISTENT_STUDENT = "Trying to update non-existent Student: ";
	public static final String ERROR_CREATE_ACCOUNT_ALREADY_EXISTS = "Trying to create an Account that exists: ";
	public static final String ERROR_CREATE_INSTRUCTOR_ALREADY_EXISTS = "Trying to create a Instructor that exists: ";
	public static final String ERROR_CREATE_STUDENT_ALREADY_EXISTS = "Trying to create a Student that exists: ";
	public static final String ERROR_TRYING_TO_MAKE_NON_EXISTENT_ACCOUNT_AN_INSTRUCTOR = "Trying to make an non-existent account an Instructor :";
	
	private static final Logger log = Common.getLogger();

	//TODO: add an updateStudent(StudentAttributes) version and make the above private
	
	/**
	  * Preconditions: 
	 * <br> * {@code instructorToAdd} is not null and has valid data.
	 */
	public void createInstructor(InstructorAttributes instructorToAdd)
			throws EntityAlreadyExistsException {
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, instructorToAdd);

		Assumption.assertTrue(
				"Invalid object received as a parameter :" + Common.toString(instructorToAdd.getInvalidStateInfo()),
				instructorToAdd.isValid());

		if (getInstructorEntityForGoogleId(instructorToAdd.courseId, instructorToAdd.googleId) != null) {
			String error = ERROR_CREATE_INSTRUCTOR_ALREADY_EXISTS
					+ instructorToAdd.googleId + ", " + instructorToAdd.courseId;
			throw new EntityAlreadyExistsException(error);
		}

		Instructor newInstructor = instructorToAdd.toEntity();
		getPM().makePersistent(newInstructor);
		getPM().flush();

		// Wait for the operation to persist
		int elapsedTime = 0;
		Instructor instructorCheck = getInstructorEntityForGoogleId(instructorToAdd.courseId, instructorToAdd.googleId);
		while ((instructorCheck == null)
				&& (elapsedTime < Common.PERSISTENCE_CHECK_DURATION)) {
			Common.waitBriefly();
			instructorCheck = getInstructorEntityForGoogleId(instructorToAdd.courseId, instructorToAdd.googleId);
			elapsedTime += Common.WAIT_DURATION;
		}
		if (elapsedTime == Common.PERSISTENCE_CHECK_DURATION) {
			log.severe("Operation did not persist in time: createInstructor->"
					+ instructorToAdd.googleId);
		}
	}
	
	/**
	 * Preconditions: <br>
	 *  * All parameters are non-null.
	 * @return empty list if no matching objects. 
	 */
	public InstructorAttributes getInstructorForEmail(String courseId, String email) {
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, email);
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, courseId);
	
		Instructor i = getInstructorEntityForEmail(courseId, email);
	
		if (i == null) {
			log.info("Trying to get non-existent Instructor: " + courseId +"/"+ email );
			return null;
		}
	
		return new InstructorAttributes(i);
	}

	
	/**
	 * Preconditions: <br>
	 *  * All parameters are non-null.
	 * @return empty list if no matching objects. 
	 */
	public InstructorAttributes getInstructorForGoogleId(String courseId, String googleId) {
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, googleId);
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, courseId);
	
		Instructor i = getInstructorEntityForGoogleId(courseId, googleId);
	
		if (i == null) {
			log.info("Trying to get non-existent Instructor: " + googleId);
			return null;
		}
	
		return new InstructorAttributes(i);
	}

	/**
	 * Preconditions: <br>
	 *  * All parameters are non-null.
	 * @return empty list if no matching objects. 
	 */
	public List<InstructorAttributes> getInstructorsForGoogleId(String googleId) {
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, googleId);
		
		List<Instructor> instructorList = getInstructorEntitiesForGoogleId(googleId);
		
		List<InstructorAttributes> instructorDataList = new ArrayList<InstructorAttributes>();
		for (Instructor i : instructorList) {
			instructorDataList.add(new InstructorAttributes(i));
		}
		
		return instructorDataList;
	}
	
	/**
	 * Preconditions: <br>
	 *  * All parameters are non-null.
	 * @return empty list if no matching objects. 
	 */
	public List<InstructorAttributes> getInstructorsForCourse(String courseId) {
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, courseId);
		
		List<Instructor> instructorList = getInstructorEntitiesForCourse(courseId);
		
		List<InstructorAttributes> instructorDataList = new ArrayList<InstructorAttributes>();
		for (Instructor i : instructorList) {
			instructorDataList.add(new InstructorAttributes(i));
		}
		
		return instructorDataList;
	}
	
	/**
	 * Not scalable. Don't use unless for admin features.
	 * @return {@code InstructorAttributes} objects for all instructor 
	 * roles in the system.
	 */
	@Deprecated
	public List<InstructorAttributes> getAllInstructors() {
		List<InstructorAttributes> list = new LinkedList<InstructorAttributes>();
		List<Instructor> entities = getInstructorEntities();
		Iterator<Instructor> it = entities.iterator();
		while(it.hasNext()) {
			list.add(new InstructorAttributes(it.next()));
		}	
		return list;
	}
	

	/**
	 * Updates the instructor. Cannot modify Course ID or email.
	 * Updates only name and email.<br>
	 * Does not follow the 'keep existing' policy <br> 
	 * Preconditions: <br>
	 * * {@code courseId} and {@code email} are non-null and correspond to an existing student. <br>
	 */
	public void updateInstructor(InstructorAttributes instructorAttributesToUpdate) {
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, instructorAttributesToUpdate);
		
		Assumption.assertTrue(
				"Invalid object received as a parameter :" + Common.toString(instructorAttributesToUpdate.getInvalidStateInfo()), 
				instructorAttributesToUpdate.isValid());
		
		Instructor instructorToUpdate = getInstructorEntityForGoogleId(instructorAttributesToUpdate.courseId, instructorAttributesToUpdate.googleId);
		
		//TODO: this should be an exception instead?
		Assumption.assertNotNull(ERROR_UPDATE_NON_EXISTENT_ACCOUNT + instructorAttributesToUpdate.googleId
				+ Common.getCurrentThreadStack(), instructorToUpdate);
		
		instructorToUpdate.setName(instructorAttributesToUpdate.name);
		instructorToUpdate.setEmail(instructorAttributesToUpdate.email);
		
		//TODO: update institute name
		//TODO: make courseId+email the non-modifiable values
		
		getPM().close();
	}
	
	/**
	 * Fails silently if no such instructor. <br>
	 * Preconditions: <br>
	 *  * All parameters are non-null.
	 */
	public void deleteInstructor(String courseId, String googleId) {
		//TODO: in future, courseId+email should be the key, not courseId+googleId

		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, googleId);
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, courseId);

		Instructor instructorToDelete = getInstructorEntityForGoogleId(courseId, googleId);

		if (instructorToDelete == null) {
			return;
		}

		getPM().deletePersistent(instructorToDelete);
		getPM().flush();

		// Check delete operation persisted
		int elapsedTime = 0;
		Instructor instructorCheck = getInstructorEntityForGoogleId(courseId, googleId);
		while ((instructorCheck != null)
				&& (elapsedTime < Common.PERSISTENCE_CHECK_DURATION)) {
			Common.waitBriefly();
			instructorCheck = getInstructorEntityForGoogleId(courseId, googleId);
			elapsedTime += Common.WAIT_DURATION;
		}
		if (elapsedTime == Common.PERSISTENCE_CHECK_DURATION) {
			log.severe("Operation did not persist in time: deleteInstructor->"
					+ googleId);
		}
	}
	
	/**
	 * Fails silently if no such instructor. <br>
	 * Preconditions: <br>
	 *  * All parameters are non-null.
	 */
	public void deleteInstructorsForGoogleId(String googleId) {
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, googleId);

		List<Instructor> instructorList = getInstructorEntitiesForGoogleId(googleId);

		getPM().deletePersistentAll(instructorList);
		getPM().flush();
	}
	
	/**
	 * Fails silently if no such instructor. <br>
	 * Preconditions: <br>
	 *  * All parameters are non-null.
	 */
	public void deleteInstructorsForCourse(String courseId) {
		Assumption.assertNotNull(Common.ERROR_DBLEVEL_NULL_INPUT, courseId);

		List<Instructor> instructorList = getInstructorEntitiesForCourse(courseId);

		getPM().deletePersistentAll(instructorList);
		getPM().flush();
	}
	
	private PersistenceManager getPM() {
		return Datastore.getPersistenceManager();
	}


	private Instructor getInstructorEntityForGoogleId(String courseId, String googleId) {
		
		Query q = getPM().newQuery(Instructor.class);
		q.declareParameters("String googleIdParam, String courseIdParam");
		q.setFilter("googleId == googleIdParam && courseId == courseIdParam");
		
		@SuppressWarnings("unchecked")
		List<Instructor> instructorList = (List<Instructor>) q.execute(googleId, courseId);
		
		if (instructorList.isEmpty()
				|| JDOHelper.isDeleted(instructorList.get(0))) {
			return null;
		}

		return instructorList.get(0);
	}
	
	private Instructor getInstructorEntityForEmail(String courseId, String email) {
		
		Query q = getPM().newQuery(Instructor.class);
		q.declareParameters("String courseIdParam, String emailParam");
		q.setFilter("courseId == courseIdParam && email == emailParam");
		
		@SuppressWarnings("unchecked")
		List<Instructor> instructorList = (List<Instructor>) q.execute(courseId, email);
		
		if (instructorList.isEmpty()
				|| JDOHelper.isDeleted(instructorList.get(0))) {
			return null;
		}

		return instructorList.get(0);
	}
	
	private List<Instructor> getInstructorEntitiesForGoogleId(String googleId) {
		
		Query q = getPM().newQuery(Instructor.class);
		q.declareParameters("String googleIdParam");
		q.setFilter("googleId == googleIdParam");
		
		@SuppressWarnings("unchecked")
		List<Instructor> instructorList = (List<Instructor>) q.execute(googleId);
		
		return instructorList;
	}

	private List<Instructor> getInstructorEntitiesForCourse(String courseId) {
		
		Query q = getPM().newQuery(Instructor.class);
		q.declareParameters("String courseIdParam");
		q.setFilter("courseId == courseIdParam");
		
		@SuppressWarnings("unchecked")
		List<Instructor> instructorList = (List<Instructor>) q.execute(courseId);
		
		return instructorList;
	}

	private List<Instructor> getInstructorEntities() {
		String query = "select from " + Instructor.class.getName();
			
		@SuppressWarnings("unchecked")
		List<Instructor> instructorList = (List<Instructor>) getPM()
				.newQuery(query).execute();
	
		return instructorList;
	}
	

}
