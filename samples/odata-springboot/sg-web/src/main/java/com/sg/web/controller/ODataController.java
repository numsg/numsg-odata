package com.sg.web.controller;


import com.sg.odata.service.NumsgODataController;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Created by numsg on 2017/3/1.
 */
@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/OdataService.svc/**")
public class ODataController extends NumsgODataController {
}
