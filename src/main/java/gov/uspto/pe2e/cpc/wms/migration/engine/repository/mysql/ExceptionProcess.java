package gov.uspto.pe2e.cpc.wms.migration.engine.repository.mysql;

import java.io.Serializable;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Lob;
import javax.persistence.Table;

import org.springframework.stereotype.Component;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@NoArgsConstructor
@AllArgsConstructor
@Data
@Component
@Table(name="exception_Process")
public class ExceptionProcess implements Serializable {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE)
	private int id;

	@Column(name="job_Id")
	private String jobId;

	@Column(name="process_Instance_Id")
	private String processInstanceId;

	@Lob
	@Column(name="analysis_Object")
	private String analysisObject;
	

}
