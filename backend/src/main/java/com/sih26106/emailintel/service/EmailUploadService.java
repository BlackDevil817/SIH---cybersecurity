package com.sih26106.emailintel.service;

import com.sih26106.emailintel.dto.UploadResponseDto;
import org.springframework.web.multipart.MultipartFile;

/**
 * Business logic for handling a new email upload. Kept out of the controller
 * per the "no business logic inside controllers" design principle.
 */
public interface EmailUploadService {

    UploadResponseDto uploadEmail(MultipartFile file);
}
