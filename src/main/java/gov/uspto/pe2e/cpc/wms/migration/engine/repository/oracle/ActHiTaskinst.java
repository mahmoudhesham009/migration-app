package gov.uspto.pe2e.cpc.wms.migration.engine.repository.oracle;

import java.io.Serializable;
import java.time.LocalDateTime;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name = "act_hi_taskinst")
public class ActHiTaskinst implements Serializable {

	private static final long serialVersionUID = 1L;

	@Id
	@Column(name = "id_")
	private String id;

	@Column(name = "proc_def_id_")
	private String procDefId;

	@Column(name = "task_def_key_")
	private String taskDefKey;

	@Column(name = "proc_inst_id_")
	private String procInstId;

	@Column(name = "execution_id_")
	private String executionId;

	@Column(name = "name_")
	private String name;

	@Column(name = "parent_task_id_")
	private String parentTaskId;

	@Column(name = "description_")
	private String description;

	@Column(name = "owner_")
	private String owner;

	@Column(name = "assignee_")
	private String assignee;

	@Column(name = "start_time_")
	private LocalDateTime startTime;

	@Column(name = "claim_time_")
	private LocalDateTime claimTime;

	@Column(name = "end_time_")
	private LocalDateTime endTime;

	@Column(name = "duration_")
	private Long duration;

	@Column(name = "delete_reason_")
	private String deleteReason;

	@Column(name = "priority_")
	private Integer priority;

	@Column(name = "due_date_")
	private LocalDateTime dueDate;

	@Column(name = "form_key_")
	private String formKey;

	@Column(name = "category_")
	private String category;

	@Column(name = "tenant_id_")
	private String tenantId;

	/**
	 * Returns the value of the field called 'id'.
	 * 
	 * @return Returns the id.
	 */
	public String getId() {
		return this.id;
	}

	/**
	 * Sets the field called 'id' to the given value.
	 * 
	 * @param id The id to set.
	 */
	public void setId(String id) {
		this.id = id;
	}

	/**
	 * Returns the value of the field called 'procDefId'.
	 * 
	 * @return Returns the procDefId.x
	 */
	public String getProcDefId() {
		return this.procDefId;
	}

	/**
	 * Sets the field called 'procDefId' to the given value.
	 * 
	 * @param procDefId The procDefId to set.
	 */
	public void setProcDefId(String procDefId) {
		this.procDefId = procDefId;
	}

	/**
	 * Returns the value of the field called 'taskDefKey'.
	 * 
	 * @return Returns the taskDefKey.
	 */
	public String getTaskDefKey() {
		return this.taskDefKey;
	}

	/**
	 * Sets the field called 'taskDefKey' to the given value.
	 * 
	 * @param taskDefKey The taskDefKey to set.
	 */
	public void setTaskDefKey(String taskDefKey) {
		this.taskDefKey = taskDefKey;
	}

	/**
	 * Returns the value of the field called 'procInstId'.
	 * 
	 * @return Returns the procInstId.
	 */
	public String getProcInstId() {
		return this.procInstId;
	}

	/**
	 * Sets the field called 'procInstId' to the given value.
	 * 
	 * @param procInstId The procInstId to set.
	 */
	public void setProcInstId(String procInstId) {
		this.procInstId = procInstId;
	}

	/**
	 * Returns the value of the field called 'executionId'.
	 * 
	 * @return Returns the executionId.
	 */
	public String getExecutionId() {
		return this.executionId;
	}

	/**
	 * Sets the field called 'executionId' to the given value.
	 * 
	 * @param executionId The executionId to set.
	 */
	public void setExecutionId(String executionId) {
		this.executionId = executionId;
	}

	/**
	 * Returns the value of the field called 'name'.
	 * 
	 * @return Returns the name.
	 */
	public String getName() {
		return this.name;
	}

	/**
	 * Sets the field called 'name' to the given value.
	 * 
	 * @param name The name to set.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * Returns the value of the field called 'parentTaskId'.
	 * 
	 * @return Returns the parentTaskId.
	 */
	public String getParentTaskId() {
		return this.parentTaskId;
	}

	/**
	 * Sets the field called 'parentTaskId' to the given value.
	 * 
	 * @param parentTaskId The parentTaskId to set.
	 */
	public void setParentTaskId(String parentTaskId) {
		this.parentTaskId = parentTaskId;
	}

	/**
	 * Returns the value of the field called 'description'.
	 * 
	 * @return Returns the description.
	 */
	public String getDescription() {
		return this.description;
	}

	/**
	 * Sets the field called 'description' to the given value.
	 * 
	 * @param description The description to set.
	 */
	public void setDescription(String description) {
		this.description = description;
	}

	/**
	 * Returns the value of the field called 'owner'.
	 * 
	 * @return Returns the owner.
	 */
	public String getOwner() {
		return this.owner;
	}

	/**
	 * Sets the field called 'owner' to the given value.
	 * 
	 * @param owner The owner to set.
	 */
	public void setOwner(String owner) {
		this.owner = owner;
	}

	/**
	 * Returns the value of the field called 'assignee'.
	 * 
	 * @return Returns the assignee.
	 */
	public String getAssignee() {
		return this.assignee;
	}

	/**
	 * Sets the field called 'assignee' to the given value.
	 * 
	 * @param assignee The assignee to set.
	 */
	public void setAssignee(String assignee) {
		this.assignee = assignee;
	}

	/**
	 * Returns the value of the field called 'startTime'.
	 * 
	 * @return Returns the startTime.
	 */

	/**
	 * Sets the field called 'startTime' to the given value.
	 * 
	 * @param startTime The startTime to set.
	 */

	/**
	 * Returns the value of the field called 'claimTime'.
	 * 
	 * @return Returns the claimTime.
	 */

	/**
	 * Sets the field called 'claimTime' to the given value.
	 * 
	 * @param claimTime The claimTime to set.
	 */

	/**
	 * Returns the value of the field called 'endTime'.
	 * 
	 * @return Returns the endTime.
	 */

	/**
	 * Sets the field called 'endTime' to the given value.
	 * 
	 * @param endTime The endTime to set.
	 */

	/**
	 * Returns the value of the field called 'duration'.
	 * 
	 * @return Returns the duration.
	 */

	/**
	 * Sets the field called 'duration' to the given value.
	 * 
	 * @param duration The duration to set.
	 */

	/**
	 * Returns the value of the field called 'deleteReason'.
	 * 
	 * @return Returns the deleteReason.
	 */
	public String getDeleteReason() {
		return this.deleteReason;
	}

	/**
	 * Sets the field called 'deleteReason' to the given value.
	 * 
	 * @param deleteReason The deleteReason to set.
	 */
	public void setDeleteReason(String deleteReason) {
		this.deleteReason = deleteReason;
	}

	public LocalDateTime getStartTime() {
		return startTime;
	}

	public void setStartTime(LocalDateTime startTime) {
		this.startTime = startTime;
	}

	public LocalDateTime getClaimTime() {
		return claimTime;
	}

	public void setClaimTime(LocalDateTime claimTime) {
		this.claimTime = claimTime;
	}

	public LocalDateTime getEndTime() {
		return endTime;
	}

	public void setEndTime(LocalDateTime endTime) {
		this.endTime = endTime;
	}

	public Long getDuration() {
		return duration;
	}

	public void setDuration(Long duration) {
		this.duration = duration;
	}

	public Number getPriority() {
		return priority;
	}

	public void setPriority(Integer priority) {
		this.priority = priority;
	}

	public static long getSerialversionuid() {
		return serialVersionUID;
	}

	public LocalDateTime getDueDate() {
		return dueDate;
	}

	public void setDueDate(LocalDateTime dueDate) {
		this.dueDate = dueDate;
	}

	/**
	 * Returns the value of the field called 'formKey'.
	 * 
	 * @return Returns the formKey.
	 */
	public String getFormKey() {
		return this.formKey;
	}

	/**
	 * Sets the field called 'formKey' to the given value.
	 * 
	 * @param formKey The formKey to set.
	 */
	public void setFormKey(String formKey) {
		this.formKey = formKey;
	}

	/**
	 * Returns the value of the field called 'category'.
	 * 
	 * @return Returns the category.
	 */
	public String getCategory() {
		return this.category;
	}

	/**
	 * Sets the field called 'category' to the given value.
	 * 
	 * @param category The category to set.
	 */
	public void setCategory(String category) {
		this.category = category;
	}

	/**
	 * Returns the value of the field called 'tenantId'.
	 * 
	 * @return Returns the tenantId.
	 */
	public String getTenantId() {
		return this.tenantId;
	}

	/**
	 * Sets the field called 'tenantId' to the given value.
	 * 
	 * @param tenantId The tenantId to set.
	 */
	public void setTenantId(String tenantId) {
		this.tenantId = tenantId;
	}

	/**
	 * Returns the value of the field called 'serialversionuid'.
	 * 
	 * @return Returns the serialversionuid.
	 */
}
