/**
 * Pagination module.
 */
var paginationModule = angular.module('ngPagination', ['ngResource', 'ngCookies']);

/**
 * Ready controller.
 */
paginationModule.controller('ReadyController', function ($scope) {
    $scope.readyCount = 0;
    $scope.ready = false;
    $scope.$watch('readyCount', function () {
        if ($scope.readyCount == 0) {
            $scope.ready = true;
        }
    });
});

/**
 * Pagination controller.
 *
 * Must be initialized by {@link init(name, url, urlParameters)} method.
 * URL must return data in format:
 *
 *     {
 *         start: <index-of-first-requested-item>,
 *         count: <total-number-of-all-items>,
 *         sort: <column-by-which-are-items-sorted>,
 *         sort-desc: <boolean-whether-sorting-is-descending>,
 *         items: [
 *             <requested-items>
 *         ]
 *     }
 *
 * $scope.ready        - can be used to determine whether data can be shown
 * $scope.errorContent - can be used to show an error
 */
paginationModule.controller('PaginationController', function ($scope, $resource, $window, $cookieStore) {
    // Resource used for fetching items
    $scope.resource = null;
    // Current page index
    $scope.pageIndex = null;
    // Current page size (number of items per page)
    $scope.pageSize = 5;
    // Specifies whether items are ready to show (e.g., they have been fetched for the first time)
    $scope.ready = false;
    // Increment parent readyCount
    if ($scope.$parent != null) {
        $scope.$parent.readyCount++;
    }
    // Error
    $scope.error = false;
    $scope.errorContent = null;
    // List of current items
    $scope.items = [];
    // List of current pages
    $scope.pages = [
        {start: 0, active: true}
    ];
    // Sorting
    $scope.sort = null;
    $scope.sortDesc = null;
    $scope.sortDefault = null;
    $scope.sortDescDefault = null;
    $scope.setSortDefault = function(sort, sortDesc) {
        $scope.sortDefault = sort;
        $scope.sortDescDefault = (sortDesc != null ? sortDesc : false);
        $scope.setSort();
    };
    $scope.setSort = function(sort, event) {
        if (typeof(event) == "boolean") {
            $scope.sort = null;
            $scope.sortDesc = event;
        }
        if (sort == null) {
            $scope.sort = $scope.sortDefault;
            $scope.sortDesc = $scope.sortDescDefault;
        }
        else if (event != null && event.shiftKey && $scope.sort != null) {
            $scope.sort = null;
            $scope.sortDesc = null;
        }
        else if ($scope.sort == sort ) {
            $scope.sortDesc = !$scope.sortDesc;
        }
        else {
            $scope.sort = sort;
            if ($scope.sortDesc == null) {
                $scope.sortDesc = false;
            }
        }
        if ($scope.ready) {
            $scope.refresh();
        }
    };

    /**
     * Test if given value is empty.
     *
     * @param value
     * @returns {boolean}
     */
    $scope.isEmpty = function(value) {
        return value == null || value == '';
    };

    /**
     * First time data is ready.
     */
    $scope.setReady = function () {
        $scope.ready = true;
        // Update parent readyCount
        if ($scope.$parent != null) {
            $scope.$parent.readyCount--;
        }
    };

    /**
     * Set fetched data.
     *
     * @param data to be set
     */
    var setData = function (data) {
        $scope.ready = true;
        $scope.error = false;
        $scope.errorContent = null;
        if ($scope.$parent != null) {
            $scope.$parent.error = false;
            $scope.$parent.errorContent = null;
        }

        // Set sorting
        if (data['sort'] != null ) {
            $scope.sort = data['sort'];
        }
        if (data['sort-desc'] != null ) {
            $scope.sortDesc = data['sort-desc'];
        }

        // Set current items
        $scope.items = data.items;
        // Create pages
        $scope.pages = [];
        var pageCount = 1;
        if ($scope.pageSize != -1) {
            pageCount = Math.floor((data.count - 1) / $scope.pageSize) + 1;
            if (pageCount == 0) {
                pageCount = 1;
            }
        }
        for (var pageIndex = 0; pageIndex < pageCount; pageIndex++) {
            var pageStart = pageIndex * $scope.pageSize;
            var pageActive = (data.start >= pageStart) && (data.start < (pageStart + $scope.pageSize));
            if (pageActive && pageIndex != $scope.pageIndex) {
                $scope.pageIndex = pageIndex;
            }
            $scope.pages.push({start: pageStart, active: pageActive});
        }
    };

    /**
     * Error happened.
     */
    $scope.setError = function (response) {
        // Get error content
        var errorContent = $('#page-content', response.data).html().trim();

        // Replace back-url by current-url
        var currentUrl = location.pathname + location.search;
        errorContent = errorContent.replace(/\"(.+(\?|&)back-url=).+\"/g, '"$1' + currentUrl + '"');

        // Set error
        $scope.error = true;
        $scope.errorContent = errorContent ;
        if ($scope.$parent != null) {
            $scope.$parent.error = true;
            $scope.$parent.errorContent = $scope.errorContent;
        }
    };

    /**
     * Initialize the controller.
     *
     * @param name of the controller for storing data to cookies
     * @param url for listing items with ":start" and ":count" parameters
     * @param urlParameters for the url
     */
    $scope.init = function (name, url, urlParameters) {
        // Setup name and resource
        $scope.name = name;
        $scope.resource = $resource(url, urlParameters, {
            list: {method: 'GET'}
        });
        // Load configuration
        var configuration = null;
        try {
            configuration = angular.fromJson($cookieStore.get(name));
        } catch (error) {
            console.warn("Failed to load pagination configuration", error);
        }
        if (configuration != null) {
            $scope.pageSize = configuration.pageSize;
        }
        // List items for the first time (to determine total count)
        $scope.performList(0, function (result) {
            setData(result);
            // If configuration is loaded set configured page index
            if (configuration != null) {
                $scope.pageSize = configuration.pageSize;
                $scope.setPage(configuration.pageIndex, function () {
                    $scope.setReady();
                });
            }
            else {
                $scope.setReady();
            }
        });
    };

    /**
     * Store current configuration (page index and page size).
     */
    $scope.storeConfiguration = function () {
        var configuration = {
            pageIndex: $scope.pageIndex,
            pageSize: $scope.pageSize
        };
        $cookieStore.put($scope.name, angular.toJson(configuration), Infinity, '/');
    };

    /**
     * Set current page.
     *
     * @param pageIndex
     * @param callback to be called after page is set
     * @param forceReload
     */
    $scope.setPage = function (pageIndex, callback, forceReload) {
        if (pageIndex == $scope.pageIndex && !forceReload) {
            if (callback != null) {
                callback.call();
            }
            return;
        }
        if (!/^\d+$/.test(pageIndex)) {
            pageIndex = 0;
        }
        else if (pageIndex < 0) {
            pageIndex = 0;
        }
        else if (pageIndex >= $scope.pages.length) {
            pageIndex = $scope.pages.length - 1;
        }

        $scope.pageIndex = pageIndex;

        // Get page
        var page = $scope.pages[pageIndex];

        // List items
        $scope.performList(page.start, function (data) {
            setData(data);
            if (callback != null) {
                callback.call();
            }

            // Store configuration
            $scope.storeConfiguration();
        });
    };

    $scope.performList = function (start, callback) {
        var listParameters = {
            'start': start,
            'count': $scope.pageSize
        };
        if ($scope.sort != null) {
            listParameters['sort'] = $scope.sort;
            listParameters['sort-desc'] = $scope.sortDesc;
        }
        $scope.ready = false;
        return $scope.resource.list(listParameters, callback, function(response){
            $scope.setError(response);
        });
    };

    /**
     * Update page sizes by current page size.
     */
    $scope.updatePageSize = function () {
        $scope.pageSize = parseInt($scope.pageSize);

        // Find new start
        var start = 0;
        if ($scope.pageSize != -1) {
            for (var pageIndex = 0; pageIndex < $scope.pages.length; pageIndex++) {
                var page = $scope.pages[pageIndex];
                if (page.active) {
                    start = page.start;
                }
            }
            start = Math.floor(start / $scope.pageSize) * $scope.pageSize;
        }

        // List items
        $scope.performList(start, function (data) {
            setData(data);

            // Store configuration
            $scope.storeConfiguration();
        });
    };

    /**
     * Refresh current page
     */
    $scope.refresh = function() {
        $scope.setPage($scope.pageIndex, null, true);
    };
});

/**
 * Directive <pagination-page-size> for displaying page size selection.
 */
paginationModule.directive('paginationPageSize', function () {
    return {
        restrict: 'E',
        compile: function (element, attrs, transclude) {
            var text = element.html();
            var attributeClass = (attrs.class != null ? attrs.class : '');
            var optionUnlimited = '';
            if ( attrs.unlimited != null ) {
                optionUnlimited = '<option value="-1">' + attrs.unlimited + '</option>';
            }
            var refresh = '';
            if ( attrs.refresh != null ) {
                refresh += '&nbsp;&nbsp;<a href="" ng-click="refresh()" class="btn" title="' + attrs.refresh +'"><span class="icon-refresh"></span></a>';
            }
            var html =
                '<div class="' + attributeClass + '">' +
                '<span ng-hide="pages.length == 1 && items.length <= 5">' + text + '&nbsp;' +
                '<select ng-model="pageSize" ng-change="updatePageSize()" style="width: 60px; margin-bottom: 0px; padding: 0px 4px; height: 24px;">' +
                '<option value="5" selected="true">5</option>' +
                '<option value="10">10</option>' +
                '<option value="15">15</option>' + optionUnlimited +
                '</select>' +
                '</span>' + refresh +
                '</div>';
            element.replaceWith(html);
        }
    }
});

/**
 * Directive <pagination-pages> for displaying page links.
 */
paginationModule.directive('paginationPages', function () {
    return {
        restrict: 'E',
        compile: function (element, attrs, transclude) {
            var text = element.html();
            var attributeClass = (attrs.class != null ? (' ' + attrs.class) : '');
            var html =
                '<div class="pagination' + attributeClass + '" style="text-align: right;">' +
                '<span ng-hide="pages.length == 1">' + text + ' ' +
                '  <span ng-repeat="page in pages">' +
                '    <a ng-hide="page.active" class="page" href="" ng-click="setPage($index)">{{$index + 1}}</a>' +
                '    <span ng-show="page.active" class="page">{{$index + 1}}</span>' +
                '  </span>' +
                '</span>' +
                '</div>';
            element.replaceWith(html);
        }
    }
});

/**
 * Directive <pagination-sort> for displaying link for sorting by a single column.
 */
paginationModule.directive('paginationSort', function () {
    return {
        restrict: 'E',
        compile: function (element, attrs, transclude) {
            var body = element.html();
            var column = attrs.column;
            var html =
                '<div style="display: inline-block;">' +
                '<a href="" ng-click="setSort(\'' + column + '\', $event)">' + body + '</a>' +
                '&nbsp;' +
                '<span class="icon-chevron-up" ng-show="sort == \'' + column + '\' && !sortDesc"></span>' +
                '<span class="icon-chevron-down" ng-show="sort == \'' + column + '\' && sortDesc"></span>' +
                '</div>';
            element.replaceWith(html);
        }
    }
});

/**
 * Directive <pagination-sort-default> for link to set default sorting.
 */
paginationModule.directive('paginationSortDefault', function () {
    return {
        restrict: 'E',
        compile: function (element, attrs, transclude) {
            var text = element.html();
            var attributeClass = (attrs.class != null ? attrs.class : '');
            var html =
                '<div class="' + attributeClass + '">' +
                '<a class="pull-right bordered" href="" ng-click="setSort()" title="' + text + '">' +
                '<i class="icon-disable-sorting"></i>' +
                '</a>' +
                '</div>';
            element.replaceWith(html);
        }
    }
});
