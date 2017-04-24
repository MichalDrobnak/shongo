<%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<link rel="stylesheet" type="text/css" href="${pageContext.request.contextPath}/css/accordion.css">
<script>
    $(document).ready(function() {
        function close_accordion_section() {
            $('.accordion .accordion-section-title').removeClass('active');
            $('.accordion .accordion-section-content').slideUp(300).removeClass('open');
        }

        $('.accordion-section-title').click(function(e) {
            // Grab current anchor value
            var currentAttrValue = $(this).attr('href');

            if($(e.target).is('.active')) {
                close_accordion_section();
            }else {
                close_accordion_section();

                // Add active class to section title
                $(this).addClass('active');
                // Open up the hidden content panel
                $('.accordion ' + currentAttrValue).slideDown(300).addClass('open');
            }

            e.preventDefault();
        });
    });

    var module = angular.module('jsp:resourceDetail', []);
    module.controller("ResourceDetailController", function($scope){


        $scope.capabilities = [
            <c:forEach items="${resource.capabilities}" var="capability">
                <c:out value="${capability.class.simpleName}"/>,
            </c:forEach> ]

    })

</script>
<div  ng-app="jsp:resourceDetail" ng-controller="ResourceDetailController" class="bordered" style="overflow:hidden">
    <dl class="dl-horizontal" style="display:inline-block; vertical-align:top;">

        <dt>Typ:</dt>
        <dd><spring:message code="${resource.type.getCode()}"/></dd>


        <dt>Jméno zdroje:</dt>
        <dd>${resource.name}</dd>

        <c:if test="${not empty resource.description}">
            <dt>Popis:</dt>
            <dd>${resource.description}</dd>
        </c:if>

        <c:if test="${not empty resource.allocatable}">
            <dt>Rezervovatelný:</dt>
            <dd>${resource.allocatable}</dd>
        </c:if>

        <c:if test="${not empty resource.calendarPublic}">
            <dt>Veřejne rezervovatelný:</dt>
            <dd>${resource.calendarPublic}</dd>
        </c:if>

        <c:if test="${not empty resource.confirmByOwner}">
            <dt>Potvrzování rezervací:</dt>
            <dd>${resource.confirmByOwner}</dd>
        </c:if>

        <c:if test="${not empty resource.maximumFuture}">
            <dt>Max. rezervovatelné na:</dt>
            <dd>${resource.maximumFuture} měsíců</dd>
        </c:if>


        <c:if test="${fn:length(resource.administratorEmails) gt 0}">
            <dt>Emaily administrátorů:</dt>
            <c:forEach items="${resource.administratorEmails}" var="email">
                <dd>${email}</dd>
            </c:forEach>
        </c:if>


        <c:if test="${resource.type == 'DEVICE_RESOURCE' and fn:length(resource.technologies) gt 0}">
            <dt>Technologie:</dt>
            <c:forEach items="${resource.technologies}" var="technology">
                <dd>${technology}</dd>
            </c:forEach>
        </c:if>
    </dl>
    <div class="full-width" style="margin:50px;">
        <h2>Vlastnosti</h2>
        <hr/>

        <c:choose>
            <c:when test="${fn:length(resource.capabilities) gt 0}">
                <div class="accordion" >
                <c:forEach items="${resource.capabilities}" var="capability" varStatus="loop">
                    <div class="accordion-section">
                        <div class="accordion-section-title" href="#accordion-${loop.index}">> <c:out value="${capability.class.simpleName}"/></div>
                        <div id="accordion-${loop.index}" class="accordion-section-content">
                                <%-- Room Provider Capability --%>
                            <c:if test="${capability['class'].simpleName == 'RoomProviderCapability'}">
                                <dl class="dl-horizontal">
                                    <dt><spring:message code="views.capabilities.RoomProvider.licencesNumber"/>:</dt>

                                    <dd><c:out value="${capability.licenseCount}"/></dd>


                                    <dt><spring:message code="views.capabilities.RoomProvider.AliasType"/>:</dt>
                                    <c:forEach items="${capability.requiredAliasTypes}" var="aliasType">
                                        <dd><spring:message code="${aliasType.name}"/></dd>
                                    </c:forEach>
                                </dl>
                            </c:if>

                                <%-- Alias Provider Capability --%>
                            <c:if test="${capability['class'].simpleName == 'AliasProviderCapability'}">
                                <dl class="dl-horizontal">
                                    <dt><spring:message code="views.capabilities.AliasProvider.aliases"/>:</dt>

                                    <c:forEach items="${capability.aliases}" var="alias">
                                        <dd>ALIAS(type: <spring:message code="${alias.type.name}"/>, value: <c:out value="${alias.value}"/>)</dd>
                                    </c:forEach>

                                    <dt>Value provider:</dt>
                                    <c:set var="valueProvider" scope="request" value="${capability.valueProvider}"/>
                                    <c:if test="${!(valueProvider['class'].simpleName == 'String')}">
                                        <div class="fc-clear"></div>
                                    </c:if>
                                    <dd><div><c:import url="/WEB-INF/views/valueProviderCapability.jsp"/></div></dd>

                                    <dt>Restricted to owner:</dt>
                                    <dd><c:out value="${alias.restrictedToOwner == true}"/></dd>
                                </dl>

                            </c:if>

                                <%-- Value Provider Capability --%>
                            <c:if test="${capability['class'].simpleName == 'ValueProviderCapability'}">

                                <c:set var="valueProvider" scope="request" value="${capability.valueProvider}"/>
                                <c:import url="/WEB-INF/views/valueProviderCapability.jsp"/>
                            </c:if>

                                <%-- Recording Capability --%>
                            <c:if test="${capability['class'].simpleName == 'RecordingCapability'}">
                                <dl class="dl-horizontal">
                                    <dt>License count:</dt>

                                    <dd><c:out value="${capability.licenseCount}"/></dd>
                                </dl>
                            </c:if>

                                <%-- Value Provider Capability --%>
                            <c:if test="${capability['class'].simpleName == 'TerminalCapability'}">
                                <dl class="dl-horizontal">
                                    <dt><spring:message code="views.capabilities.AliasProvider.aliases"/>:</dt>
                                    <c:forEach items="${capability.aliases}" var="alias">
                                        <dd>ALIAS(type: <spring:message code="${alias.type.name}"/>, value: <c:out value="${alias.value}"/>)</dd>
                                    </c:forEach>
                                </dl>


                            </c:if>

                        </div>
                    </div>
                </c:forEach>
                </div>
            </c:when>
            <c:otherwise>
                <div class="center-content">- - - none - - -</div>
            </c:otherwise>
        </c:choose>
    </div>

</div>
