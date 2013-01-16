#!/usr/bin/sh

bin/client-cli.sh --connect 127.0.0.1:8686 --testing-access-token --scripting \
--cmd "\
    create-reservation-request { \
        class: 'ReservationRequest', \
        description: 'test', \
        purpose: 'SCIENCE', \
        slot: '`date +"%Y-%m-%d"`T12:00/PT1H', \
        specification: { \
            class: 'RoomSpecification', \
            technologies: ['H323'], \
            participantCount: 5 \
        } \
    }" \
--cmd "\
    create-reservation-request { \
        class: 'ReservationRequest', \
        description: 'test', \
        purpose: 'SCIENCE', \
        slot: '`date +"%Y-%m-%d"`T12:00/PT1H', \
        specification: { \
            class: 'CompartmentSpecification', \
            specifications: [{ \
                class: 'ExternalEndpointSetSpecification', \
                technologies: ['H323'], \
                count: 3 \
            }] \
        } \
    }" \

echo "Waiting for allocation..."
sleep 2

bin/client-cli.sh --connect 127.0.0.1:8686 --testing-access-token --scripting \
--cmd "list-reservation-requests" \
--cmd "get-reservation-request 1" \
--cmd "get-reservation-for-request 1" \
--cmd "get-reservation-request 2" \
--cmd "get-reservation-for-request 2" \
--cmd "list-executables" \
--cmd "get-executable 1" \
--cmd "get-executable 2" \
