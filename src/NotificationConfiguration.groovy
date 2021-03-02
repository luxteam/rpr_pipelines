public class NotificationConfiguration {

    def static PLUGIN_NOT_FOUND = "Plugin not found during cache building."

    def static FAILED_TO_VERIFY_MATLIB = "Failed to verify installed MatLib."

    def static FAILED_TO_INSTALL_MATLIB = "Failed to install MatLib."

    def static NO_OUTPUT_IMAGE = "No output image after cache building."
    
    def static ENGINES_PARAM = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "Engines parameter is required.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static DELEGATES_PARAM = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "Delegates parameter is required.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static INITIALIZATION = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed initialization.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.GENERAL
            ]
        ]
    ]

    def static DOWNLOAD_SOURCE_CODE_REPO = [
        "begin": ["message": "Downloading source code repository."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to download source code repository due to timeout.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "timed_out"]
            ],
            [
                "class": Exception, "problemMessage": "Failed to merge branches.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "getMessage": ["Branch not suitable for integration"],
                "githubNotification": ["status": "failure"]
            ],
            [
                "class": Exception, "problemMessage": "Failed to download source code repository.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "failure"]
            ]
        ]
    ]

    def static DOWNLOAD_UNIT_TESTS_REPO = [
        "begin": ["message": "Downloading unit tests repository."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to download unit tests repository due to timeout.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "timed_out"]
            ],
            [
                "class": Exception, "problemMessage": "Failed to download unit tests repository.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "failure"]
            ]
        ]
    ]

    def static DOWNLOAD_USD_REPO = [
        "begin": ["message": "Downloading the USD repository."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to download the USD repository.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "failure"]
            ]
        ]
    ]

    def static INCREMENT_VERSION = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to increment version.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "action_required"]
            ]
        ]
    ]

    def static CONFIGURE_TESTS = [
        "end": ["message": "PreBuild stage was successfully finished."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to configurate tests.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "action_required"]
            ]
        ]
    ]

    def static DOWNLOAD_JOBS_LAUNCHER = [
        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "[WARNING] Failed to download jobs launcher due to timeout.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "timed_out"]
            ],
            [
                "class": Exception, "problemMessage": "[WARNING] Failed to download jobs launcher.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static BUILD_SOURCE_CODE = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to build the project.", 
                "rethrow": ExceptionThrowType.RETHROW,
                "githubNotification": ["status": "failure"]
            ]
        ]
    ]

    def static BUILD_PACKAGE = [
        "begin": ["message": "Creating package."],

        "end": ["message": "Package was successfully created."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to create package.", 
                "rethrow": ExceptionThrowType.RETHROW,
                "githubNotification": ["status": "failure"]
            ]
        ]
    ]

    def static DOWNLOAD_SCENES = [
        "begin": ["message": "Downloading test scenes."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to download test scenes.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static DOWNLOAD_TESTS_REPO = [
        "begin": ["message": "Downloading tests repository."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to download tests repository due to timeout.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "timed_out"]
            ],
            [
                "class": Exception, "problemMessage": "Failed to download tests repository.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static DOWNLOAD_PACKAGE = [
        "begin": ["message": "Downloading package."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to download package.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,
            ]
        ]
    ]

    def static INSTALL_PLUGIN = [
        "begin": ["message": "Installing the plugin."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to install the plugin due to timeout.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,
                "githubNotification": ["status": "timed_out"]
            ],
            [
                "class": Exception, "problemMessage": "Failed to install the plugin.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static INSTALL_HOUDINI = [
        "begin": ["message": "Installing Houdini."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to validate installed Houdini or install houdini due to timeout.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,
                "githubNotification": ["status": "timed_out"]
            ],
            [
                "class": Exception, "problemMessage": "Failed to validate installed houdini or install houdini.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static BUILD_CACHE = [
        "begin": ["message": "Building cache."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to build cache due to timeout.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,
                "githubNotification": ["status": "timed_out"]
            ],
            [
                "class": Exception, "problemMessage": PLUGIN_NOT_FOUND, 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "getMessage": [PLUGIN_NOT_FOUND]
            ],
            [
                "class": Exception, "problemMessage": FAILED_TO_VERIFY_MATLIB, 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "getMessage": [FAILED_TO_VERIFY_MATLIB]
            ],
            [
                "class": Exception, "problemMessage": FAILED_TO_INSTALL_MATLIB, 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "getMessage": [FAILED_TO_INSTALL_MATLIB]
            ],
            [
                "class": Exception, "problemMessage": NO_OUTPUT_IMAGE, 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "getMessage": [NO_OUTPUT_IMAGE]
            ],
            [
                "class": Exception, "problemMessage": "Failed to build cache.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static COPY_BASELINES = [
        "begin": ["message": "Downloading reference images."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "[WARNING] Problem when copying baselines.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static EXECUTE_TESTS = [
        "begin": ["message": "Executing tests."],

        "exceptions": [
            [
                "class": "TimeoutExceeded", "problemMessage": "Failed to execute tests due to timeout.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER,
                "githubNotification": ["status": "timed_out"]
            ],
            [
                "class": Exception, "problemMessage": "An error occurred while executing tests. Please contact support.", 
                "rethrow": ExceptionThrowType.THROW_IN_WRAPPER
            ]
        ]
    ]

    def static PUBLISH_REPORT = [
        "begin": ["message": "Publishing test report."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to publish test report.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "failure"]
            ]
        ]
    ]

    def static BUILDING_UNIT_TESTS_REPORT = [
        "begin": ["message": "Building test report for unit tests."],

        "exceptions": [
            [
                "class": Exception, "problemMessage": "Failed to build test report for unit tests.", 
                "rethrow": ExceptionThrowType.RETHROW, "scope": ProblemMessageManager.SPECIFIC,
                "githubNotification": ["status": "failure"]
            ]
        ]
    ]

    def static CREATE_GITHUB_NOTIFICATOR = [
        "exceptions": [
            [
                "class": Exception, "problemMessage": "[WARNING] Failed to create GithubNotificator.", 
                "rethrow": ExceptionThrowType.NO, "scope": ProblemMessageManager.SPECIFIC
            ]
        ]
    ]

    def static PRE_BUILD_STAGE_FAILED = "PreBuild stage was failed."

    def static BUILD_SOURCE_CODE_START_MESSAGE = "Building the project."

    def static BUILD_SOURCE_CODE_END_MESSAGE = "The project was successfully built and published."

    def static REASON_IS_NOT_IDENTIFIED = "The reason is not automatically identified. Please contact support."

    def static SOME_TESTS_ERRORED = "Some tests were marked as error. Check the report for details."

    def static SOME_TESTS_FAILED = "Some tests were marked as failed. Check the report for details."

    def static ALL_TESTS_PASSED = "Tests completed successfully."

    def static FAILED_TO_SAVE_RESULTS = "An error occurred while saving test results. Please contact support."

    def static BUILDING_REPORT = "Building test report."

    def static REPORT_PUBLISHED = "Report was published successfully."

    def static TIMEOUT_EXCEEDED = "Timeout exceeded."

    def static UNKNOWN_REASON = "Unknown reason."

    def static STAGE_TIMEOUT_EXCEEDED = "Stage timeout exceeded."

    def static BUILD_ABORTED_BY_COMMIT = "Build was aborted by new commit."

    def static BUILD_ABORTED_BY_USER = "Build was aborted by user."

    def static LOST_CONNECTION_WITH_MACHINE = "Lost connection with machine. Please contact support."

    def static CAN_NOT_GET_TESTS_STATUS = "Can't get tests status."

    def static EXECUTE_UNIT_TESTS = "Executing unit tests."

    def static INVALID_PLUGIN_SIZE = "Invalid plugin (size is less than 10Mb)."

    def static CORRUPTED_ZIP_PLUGIN = "Corrupted .zip plugin."

    def static CORRUPTED_MSI_PLUGIN = "Corrupted .msi plugin."

}
